package com.example.netting.service;

import com.example.netting.domain.BatchStatus;
import com.example.netting.domain.Claim;
import com.example.netting.domain.ClaimStatus;
import com.example.netting.domain.ExclusionReason;
import com.example.netting.domain.FxQuote;
import com.example.netting.domain.NettingAgreement;
import com.example.netting.domain.NettingBatch;
import com.example.netting.domain.NettingGroup;
import com.example.netting.domain.ResidualBearer;
import com.example.netting.domain.Settlement;
import com.example.netting.domain.ClaimAllocation;
import com.example.netting.domain.LegalEntity;
import com.example.netting.dto.BatchSummaryDto;
import com.example.netting.dto.TrialRequest;
import com.example.netting.dto.TrialResponse;
import com.example.netting.repository.AgreementPartyRepository;
import com.example.netting.repository.ClaimRepository;
import com.example.netting.repository.NettingBatchRepository;
import com.example.netting.service.NettingModel.AgreementRef;
import com.example.netting.service.NettingModel.Calculation;
import com.example.netting.service.NettingModel.ClaimRef;
import com.example.netting.service.NettingModel.FxRate;
import com.example.netting.service.NettingModel.FxRateKey;
import com.example.netting.service.NettingModel.GroupResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BatchService {

    private static final String MANUAL_FX_SOURCE = "MANUAL_TRIAL_INPUT";

    private final ClaimRepository claimRepository;
    private final AgreementPartyRepository agreementPartyRepository;
    private final NettingBatchRepository batchRepository;
    private final NettingCalculator calculator;
    private final ObjectMapper objectMapper;

    public BatchService(ClaimRepository claimRepository, AgreementPartyRepository agreementPartyRepository,
                        NettingBatchRepository batchRepository, NettingCalculator calculator,
                        ObjectMapper objectMapper) {
        this.claimRepository = claimRepository;
        this.agreementPartyRepository = agreementPartyRepository;
        this.batchRepository = batchRepository;
        this.calculator = calculator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TrialResponse runTrial(TrialRequest request) {
        Instant calculatedAt = Instant.now();
        List<Claim> allClaims = claimRepository.findAllByOrderByInvoiceNumber();
        Map<Long, Set<Long>> partyIdsByAgreement = loadPartyIds();
        Map<Long, LegalEntity> entityById = allClaims.stream()
                .flatMap(claim -> List.of(claim.getCreditor(), claim.getDebtor()).stream())
                .distinct()
                .collect(Collectors.toMap(LegalEntity::getId, Function.identity()));
        Map<Long, NettingAgreement> agreementById = allClaims.stream()
                .map(Claim::getAgreement)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toMap(NettingAgreement::getId, Function.identity()));

        List<EligibleClaim> eligibleClaims = new ArrayList<>();
        List<TrialResponse.ExcludedClaimDto> excludedClaims = new ArrayList<>();
        for (Claim claim : allClaims) {
            List<ExclusionReason> reasons = exclusionReasons(claim, request, partyIdsByAgreement, calculatedAt, true);
            if (reasons.isEmpty()) {
                eligibleClaims.add(new EligibleClaim(claim, toClaimRef(claim)));
            } else {
                excludedClaims.add(new TrialResponse.ExcludedClaimDto(toClaimDto(claim), reasons));
            }
        }

        if (eligibleClaims.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No eligible open claims match the trial scope");
        }

        Map<FxRateKey, FxRate> fxRates = toFxRates(request);
        Calculation calculation = calculator.calculate(eligibleClaims.stream()
                        .map(EligibleClaim::ref)
                        .toList(),
                request.targetCurrency(),
                request.residualBearer() == null ? ResidualBearer.PAYER : request.residualBearer(),
                fxRates);

        String trialInput = writeJson(TrialInput.fromClaims(request,
                eligibleClaims.stream().map(EligibleClaim::claim).toList()));
        String signature = sha256(trialInput);
        NettingBatch batch = persistTrial(request, eligibleClaims.stream().map(EligibleClaim::claim).toList(),
                calculation, trialInput, signature, calculatedAt);
        return toResponse(batch, eligibleClaims.stream().map(EligibleClaim::claim).toList(),
                excludedClaims, calculation, request, entityById, agreementById, partyIdsByAgreement);
    }

    @Transactional
    public TrialResponse confirmBatch(UUID batchId) {
        NettingBatch batch = batchRepository.findWithDetailsByExternalId(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found"));
        if (batch.getStatus() != BatchStatus.TRIAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only trial batches can be confirmed");
        }

        TrialInput storedInput = readValue(batch.getTrialInput(), TrialInput.class);
        List<Long> sourceIds = storedInput.claims().stream().map(ClaimSnapshot::id).toList();
        List<Claim> currentSourceClaims = claimRepository.findAllById(sourceIds);
        String currentSignature = sha256(writeJson(TrialInput.fromClaims(storedInput.request(), currentSourceClaims)));
        if (!MessageDigest.isEqual(currentSignature.getBytes(StandardCharsets.UTF_8),
                batch.getInputSignature().getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Source claims changed after the trial; rerun the calculation before confirmation");
        }

        Set<Long> allocatedClaims = batch.getGroups().stream()
                .flatMap(group -> group.getAllocations().stream())
                .map(ClaimAllocation::getClaimId)
                .collect(Collectors.toSet());
        for (Long claimId : allocatedClaims) {
            Claim claim = claimRepository.findById(claimId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Original claim is missing"));
            claim.markNetted();
        }
        batch.confirm(Instant.now());
        return getBatch(batchId);
    }

    @Transactional
    public TrialResponse getBatch(UUID batchId) {
        NettingBatch batch = batchRepository.findWithDetailsByExternalId(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found"));
        TrialInput input = readValue(batch.getTrialInput(), TrialInput.class);
        List<Claim> allClaims = claimRepository.findAllByOrderByInvoiceNumber();
        Map<Long, Set<Long>> parties = loadPartyIds();
        Map<Long, LegalEntity> entityById = allClaims.stream()
                .flatMap(claim -> List.of(claim.getCreditor(), claim.getDebtor()).stream())
                .distinct()
                .collect(Collectors.toMap(LegalEntity::getId, Function.identity()));
        Map<Long, NettingAgreement> agreements = allClaims.stream()
                .map(Claim::getAgreement)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toMap(NettingAgreement::getId, Function.identity()));

        Set<Long> eligibleIds = batch.getGroups().stream()
                .flatMap(group -> group.getAllocations().stream())
                .map(ClaimAllocation::getClaimId)
                .collect(Collectors.toSet());
        List<Claim> eligible = allClaims.stream().filter(claim -> eligibleIds.contains(claim.getId())).toList();
        List<TrialResponse.ExcludedClaimDto> excluded = allClaims.stream()
                .filter(claim -> !eligibleIds.contains(claim.getId()))
                .map(claim -> new TrialResponse.ExcludedClaimDto(toClaimDto(claim),
                        exclusionReasons(claim, input.request(), parties, batch.getCalculatedAt(), false)))
                .toList();

        Calculation calculation = calculator.calculate(eligible.stream().map(this::toClaimRef).toList(),
                input.request().targetCurrency(),
                input.request().residualBearer() == null ? ResidualBearer.PAYER : input.request().residualBearer(),
                toFxRates(input.request()));
        return toResponse(batch, eligible, excluded, calculation, input.request(), entityById, agreements, parties);
    }

    @Transactional
    public List<BatchSummaryDto> listBatches() {
        return batchRepository.findAllByOrderByCalculatedAtDesc().stream()
                .map(batch -> new BatchSummaryDto(batch.getExternalId(), batch.getBatchRef(), batch.getStatus(),
                        batch.getTargetCurrency(), batch.getResidualBearer(), batch.getGrossPaymentCount(),
                        batch.getResidualPaymentCount(), batch.getEliminatedPaymentCount(),
                        batch.getCalculatedAt(), batch.getConfirmedAt()))
                .toList();
    }

    public List<TrialResponse.ClaimDto> listClaims() {
        return claimRepository.findAllByOrderByInvoiceNumber().stream().map(this::toClaimDto).toList();
    }

    private NettingBatch persistTrial(TrialRequest request, List<Claim> eligibleClaims,
                                      Calculation calculation, String trialInput, String signature,
                                      Instant calculatedAt) {
        List<CurrencyTotalValue> totals = currencyTotals(calculation);
        NettingBatch batch = new NettingBatch(
                UUID.randomUUID(),
                "NB-" + calculatedAt.atZone(ZoneOffset.UTC).toLocalDate() + "-"
                        + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                request.targetCurrency(),
                request.residualBearer() == null ? ResidualBearer.PAYER : request.residualBearer(),
                eligibleClaims.size(),
                calculation.groups().stream().mapToInt(GroupResult::residualPaymentCount).sum(),
                eligibleClaims.size() - calculation.groups().stream()
                        .mapToInt(GroupResult::residualPaymentCount).sum(),
                writeJson(totals),
                trialInput,
                signature,
                calculatedAt
        );

        Map<Long, LegalEntity> entities = eligibleClaims.stream()
                .flatMap(claim -> List.of(claim.getCreditor(), claim.getDebtor()).stream())
                .collect(Collectors.toMap(LegalEntity::getId, Function.identity(), (left, right) -> left,
                        LinkedHashMap::new));

        for (GroupResult result : calculation.groups()) {
            NettingGroup group = new NettingGroup(result.agreement().id(), result.agreement().code(),
                    result.currency(), result.grossPaymentCount(), result.residualPaymentCount(),
                    result.mutualOffsetAmount(), result.grossAmount(), result.residualAmount());

            Map<Integer, Settlement> settlementBySequence = new LinkedHashMap<>();
            for (NettingModel.Settlement settlementResult : result.settlements()) {
                FxQuote fxQuote = null;
                if (settlementResult.fx() != null) {
                    NettingModel.FxInstruction fx = settlementResult.fx();
                    LegalEntity residualEntity = entities.get(fx.residualEntity().id());
                    fxQuote = new FxQuote(fx.fromCurrency(), fx.toCurrency(), fx.rate(), fx.rateTime(),
                            fx.source(), fx.residualBearer(), fx.residualAmount(), residualEntity.getId(),
                            residualEntity.getCode());
                }
                Settlement settlement = new Settlement(settlementResult.payer().id(),
                        settlementResult.payer().code(), settlementResult.receiver().id(),
                        settlementResult.receiver().code(), settlementResult.amount(),
                        settlementResult.currency(), settlementResult.sequence(),
                        settlementResult.fx() == null ? null : settlementResult.fx().convertedAmount(),
                        settlementResult.fx() == null ? null : settlementResult.fx().residualAmount(),
                        fxQuote);
                group.addSettlement(settlement);
                settlementBySequence.put(settlementResult.sequence(), settlement);
            }

            for (NettingModel.Allocation allocation : result.trace()) {
                ClaimAllocation entity = new ClaimAllocation(allocation.claim().id(),
                        allocation.claim().invoiceNumber(), allocation.claim().debtor().id(),
                        allocation.claim().creditor().id(), allocation.type(), allocation.allocatedAmount());
                group.addAllocation(entity);
                if (allocation.settlementSequence() != null) {
                    Settlement settlement = settlementBySequence.get(allocation.settlementSequence());
                    if (settlement != null) {
                        settlement.addAllocation(entity);
                    }
                }
            }
            batch.addGroup(group);
        }
        return batchRepository.save(batch);
    }

    private TrialResponse toResponse(NettingBatch batch, List<Claim> eligibleClaims,
                                     List<TrialResponse.ExcludedClaimDto> excludedClaims,
                                     Calculation calculation, TrialRequest request,
                                     Map<Long, LegalEntity> entities,
                                     Map<Long, NettingAgreement> agreements,
                                     Map<Long, Set<Long>> parties) {
        List<TrialResponse.ClaimDto> claimDtos = eligibleClaims.stream().map(this::toClaimDto).toList();
        List<TrialResponse.GroupResultDto> groupDtos = calculation.groups().stream()
                .map(group -> toGroupDto(group, entities, agreements))
                .toList();
        return new TrialResponse(
                batch.getExternalId(),
                batch.getBatchRef(),
                batch.getStatus(),
                batch.getTargetCurrency(),
                batch.getResidualBearer(),
                request.fxRateTime(),
                batch.getCalculatedAt(),
                batch.getConfirmedAt(),
                batch.getGrossPaymentCount(),
                batch.getResidualPaymentCount(),
                batch.getEliminatedPaymentCount(),
                readCurrencyTotals(batch.getAmountTotals()),
                claimDtos,
                excludedClaims.stream()
                        .sorted(Comparator.comparing(item -> item.claim().invoiceNumber()))
                        .toList(),
                groupDtos,
                calculation.balanceChecks().stream()
                        .map(check -> new TrialResponse.BalanceCheckDto(check.agreementCode(), check.currency(),
                                toEntityDto(entities.get(check.entity().id())), check.originalNetPosition(),
                                check.proposedNetPosition(), check.difference()))
                        .toList(),
                batch.getInputSignature()
        );
    }

    private TrialResponse.GroupResultDto toGroupDto(GroupResult group, Map<Long, LegalEntity> entities,
                                                    Map<Long, NettingAgreement> agreements) {
        return new TrialResponse.GroupResultDto(
                group.agreement().code(),
                group.agreement().name(),
                group.currency(),
                group.grossPaymentCount(),
                group.residualPaymentCount(),
                group.grossAmount(),
                group.mutualOffsetAmount(),
                group.residualAmount(),
                group.positions().stream()
                        .map(position -> new TrialResponse.PositionDto(toEntityDto(entities.get(position.entity().id())),
                                position.payable(), position.receivable(), position.netPosition()))
                        .toList(),
                group.settlements().stream().map(settlement -> toSettlementDto(settlement, entities)).toList(),
                group.trace().stream().map(allocation -> toTraceDto(allocation, entities)).toList()
        );
    }

    private TrialResponse.SettlementDto toSettlementDto(NettingModel.Settlement settlement,
                                                        Map<Long, LegalEntity> entities) {
        TrialResponse.FxResultDto fx = null;
        if (settlement.fx() != null) {
            NettingModel.FxInstruction source = settlement.fx();
            fx = new TrialResponse.FxResultDto(source.fromCurrency(), source.toCurrency(), source.rate(),
                    source.rateTime(), source.source(), source.convertedAmount(), source.residualAmount(),
                    source.residualBearer(), toEntityDto(entities.get(source.residualEntity().id())));
        }
        return new TrialResponse.SettlementDto(settlement.sequence(),
                toEntityDto(entities.get(settlement.payer().id())),
                toEntityDto(entities.get(settlement.receiver().id())),
                settlement.amount(), settlement.currency(), fx,
                settlement.allocations().stream().map(allocation -> toTraceDto(allocation, entities)).toList());
    }

    private TrialResponse.TraceItemDto toTraceDto(NettingModel.Allocation allocation,
                                                  Map<Long, LegalEntity> entities) {
        ClaimRef claim = allocation.claim();
        return new TrialResponse.TraceItemDto(claim.invoiceNumber(),
                toEntityDto(entities.get(claim.creditor().id())),
                toEntityDto(entities.get(claim.debtor().id())),
                claim.amount(), allocation.allocatedAmount(), allocation.type(),
                allocation.settlementSequence());
    }

    private TrialResponse.EntityRefDto toEntityDto(LegalEntity entity) {
        return new TrialResponse.EntityRefDto(entity.getId(), entity.getCode(), entity.getName());
    }

    private TrialResponse.AgreementDto toAgreementDto(NettingAgreement agreement) {
        if (agreement == null) {
            return null;
        }
        return new TrialResponse.AgreementDto(agreement.getId(), agreement.getCode(), agreement.getName());
    }

    private TrialResponse.ClaimDto toClaimDto(Claim claim) {
        return new TrialResponse.ClaimDto(claim.getId(), claim.getInvoiceNumber(), toEntityDto(claim.getCreditor()),
                toEntityDto(claim.getDebtor()), toAgreementDto(claim.getAgreement()), claim.getAmount(),
                claim.getCurrency(), claim.getInvoiceDate(), claim.getDueDate(), claim.getStatus(),
                claim.isPledged(), claim.isDisputed(), claim.getDescription());
    }

    private ClaimRef toClaimRef(Claim claim) {
        return new ClaimRef(claim.getId(), claim.getInvoiceNumber(),
                new NettingModel.EntityRef(claim.getCreditor().getId(), claim.getCreditor().getCode(),
                        claim.getCreditor().getName()),
                new NettingModel.EntityRef(claim.getDebtor().getId(), claim.getDebtor().getCode(),
                        claim.getDebtor().getName()),
                new AgreementRef(claim.getAgreement().getId(), claim.getAgreement().getCode(),
                        claim.getAgreement().getName()),
                claim.getAmount(), claim.getCurrency(), claim.isPledged(), claim.isDisputed());
    }

    private List<ExclusionReason> exclusionReasons(Claim claim, TrialRequest request,
                                                   Map<Long, Set<Long>> parties, Instant at,
                                                   boolean includeNotOpenReason) {
        List<ExclusionReason> reasons = new ArrayList<>();
        if (includeNotOpenReason && claim.getStatus() != ClaimStatus.OPEN) {
            reasons.add(ExclusionReason.NOT_OPEN);
        }
        if (claim.isPledged()) {
            reasons.add(ExclusionReason.PLEDGED);
        }
        if (claim.isDisputed()) {
            reasons.add(ExclusionReason.DISPUTED);
        }
        NettingAgreement agreement = claim.getAgreement();
        if (agreement == null) {
            reasons.add(ExclusionReason.MISSING_AGREEMENT);
        } else {
            if (request.agreementCode() != null
                    && !request.agreementCode().equalsIgnoreCase(agreement.getCode())) {
                reasons.add(ExclusionReason.AGREEMENT_SCOPE_FILTERED);
            }
            if (request.currency() != null && !request.currency().equalsIgnoreCase(claim.getCurrency())) {
                reasons.add(ExclusionReason.CURRENCY_FILTERED);
            }
            Set<Long> partyIds = parties.getOrDefault(agreement.getId(), Set.of());
            if (!partyIds.contains(claim.getCreditor().getId())
                    || !partyIds.contains(claim.getDebtor().getId())) {
                reasons.add(ExclusionReason.NOT_PARTY_TO_AGREEMENT);
            }
            Instant effectiveAt = request.fxRateTime() == null ? at : request.fxRateTime();
            if (agreement.getEffectiveFrom().isAfter(effectiveAt)
                    || (agreement.getEffectiveTo() != null && agreement.getEffectiveTo().isBefore(effectiveAt))) {
                reasons.add(ExclusionReason.AGREEMENT_NOT_IN_EFFECT);
            }
        }
        return reasons;
    }

    private Map<Long, Set<Long>> loadPartyIds() {
        return agreementPartyRepository.findAll().stream()
                .collect(Collectors.groupingBy(party -> party.getAgreement().getId(),
                        Collectors.mapping(party -> party.getParty().getId(), Collectors.toSet())));
    }

    private Map<FxRateKey, FxRate> toFxRates(TrialRequest request) {
        if (request.fxRates() == null) {
            return Map.of();
        }
        Instant rateTime = Optional.ofNullable(request.fxRateTime()).orElse(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        Map<FxRateKey, FxRate> rates = new LinkedHashMap<>();
        for (TrialRequest.FxRateInput input : request.fxRates()) {
            FxRateKey key = new FxRateKey(input.fromCurrency(), input.toCurrency());
            rates.put(key, new FxRate(input.rate(), rateTime, MANUAL_FX_SOURCE));
        }
        return rates;
    }

    private List<CurrencyTotalValue> currencyTotals(Calculation calculation) {
        Map<String, CurrencyTotalValue> totals = new LinkedHashMap<>();
        for (GroupResult group : calculation.groups()) {
            CurrencyTotalValue total = totals.computeIfAbsent(group.currency(),
                    ignored -> new CurrencyTotalValue(group.currency()));
            total.add(group.grossAmount(), group.residualAmount(), group.mutualOffsetAmount());
        }
        return new ArrayList<>(totals.values());
    }

    private List<TrialResponse.CurrencyTotal> readCurrencyTotals(String json) {
        List<CurrencyTotalValue> values = readList(json, CurrencyTotalValue.class);
        return values.stream()
                .map(value -> new TrialResponse.CurrencyTotal(value.currency, value.grossAmount,
                        value.residualAmount, value.mutualOffsetAmount))
                .toList();
    }

    private <T> List<T> readList(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, type));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to read stored JSON", e);
        }
    }

    private <T> T readValue(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to read stored trial input", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize trial data", e);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private record EligibleClaim(Claim claim, ClaimRef ref) {
    }

    public record TrialInput(TrialRequest request, List<ClaimSnapshot> claims) {
        public static TrialInput fromClaims(TrialRequest request, List<Claim> claims) {
            return new TrialInput(request, claims.stream()
                    .sorted(Comparator.comparing(Claim::getId))
                    .map(ClaimSnapshot::new)
                    .toList());
        }
    }

    public record ClaimSnapshot(Long id, String invoiceNumber, Long creditorId, Long debtorId, Long agreementId,
                                String amount, String currency, String status, boolean pledged,
                                boolean disputed, java.time.Instant claimedAt) {
        private ClaimSnapshot(Claim claim) {
            this(claim.getId(), claim.getInvoiceNumber(), claim.getCreditor().getId(), claim.getDebtor().getId(),
                    claim.getAgreement() == null ? null : claim.getAgreement().getId(),
                    claim.getAmount().toPlainString(), claim.getCurrency(), claim.getStatus().name(),
                    claim.isPledged(), claim.isDisputed(), claim.getClaimedAt());
        }
    }

    public static class CurrencyTotalValue {
        public String currency;
        public java.math.BigDecimal grossAmount = java.math.BigDecimal.ZERO;
        public java.math.BigDecimal residualAmount = java.math.BigDecimal.ZERO;
        public java.math.BigDecimal mutualOffsetAmount = java.math.BigDecimal.ZERO;

        public CurrencyTotalValue() {
        }

        public CurrencyTotalValue(String currency) {
            this.currency = currency;
        }

        public void add(java.math.BigDecimal gross, java.math.BigDecimal residual, java.math.BigDecimal offset) {
            grossAmount = grossAmount.add(gross);
            residualAmount = residualAmount.add(residual);
            mutualOffsetAmount = mutualOffsetAmount.add(offset);
        }
    }
}
