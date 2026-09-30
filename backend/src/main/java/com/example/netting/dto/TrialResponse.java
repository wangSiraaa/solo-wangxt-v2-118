package com.example.netting.dto;

import com.example.netting.domain.AllocationType;
import com.example.netting.domain.BatchStatus;
import com.example.netting.domain.ClaimStatus;
import com.example.netting.domain.ExclusionReason;
import com.example.netting.domain.ResidualBearer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TrialResponse(
        UUID batchId,
        String batchRef,
        BatchStatus status,
        String targetCurrency,
        ResidualBearer residualBearer,
        Instant fxRateTime,
        Instant calculatedAt,
        Instant confirmedAt,
        int grossPaymentCount,
        int residualPaymentCount,
        int eliminatedPaymentCount,
        List<CurrencyTotal> amountTotals,
        List<ClaimDto> eligibleClaims,
        List<ExcludedClaimDto> excludedClaims,
        List<GroupResultDto> groups,
        List<BalanceCheckDto> balanceChecks,
        String inputSignature
) {

    public record EntityRefDto(Long id, String code, String name) {
    }

    public record AgreementDto(Long id, String code, String name) {
    }

    public record ClaimDto(
            Long id,
            String invoiceNumber,
            EntityRefDto creditor,
            EntityRefDto debtor,
            AgreementDto agreement,
            BigDecimal amount,
            String currency,
            LocalDate invoiceDate,
            LocalDate dueDate,
            ClaimStatus status,
            boolean pledged,
            boolean disputed,
            String description
    ) {
    }

    public record ExcludedClaimDto(
            ClaimDto claim,
            List<ExclusionReason> reasons
    ) {
    }

    public record GroupResultDto(
            String agreementCode,
            String agreementName,
            String currency,
            int grossPaymentCount,
            int residualPaymentCount,
            BigDecimal grossAmount,
            BigDecimal mutualOffsetAmount,
            BigDecimal residualAmount,
            List<PositionDto> positions,
            List<SettlementDto> settlements,
            List<TraceItemDto> trace
    ) {
    }

    public record PositionDto(
            EntityRefDto entity,
            BigDecimal payable,
            BigDecimal receivable,
            BigDecimal netPosition
    ) {
    }

    public record SettlementDto(
            int sequenceNo,
            EntityRefDto payer,
            EntityRefDto receiver,
            BigDecimal amount,
            String currency,
            FxResultDto fx,
            List<TraceItemDto> trace
    ) {
    }

    public record FxResultDto(
            String fromCurrency,
            String toCurrency,
            BigDecimal rate,
            Instant rateTime,
            String source,
            BigDecimal convertedAmount,
            BigDecimal residualAmount,
            ResidualBearer residualBearer,
            EntityRefDto residualEntity
    ) {
    }

    public record TraceItemDto(
            String invoiceNumber,
            EntityRefDto creditor,
            EntityRefDto debtor,
            BigDecimal originalAmount,
            BigDecimal allocatedAmount,
            AllocationType allocationType,
            Integer settlementSequenceNo
    ) {
    }

    public record BalanceCheckDto(
            String agreementCode,
            String currency,
            EntityRefDto entity,
            BigDecimal originalNetPosition,
            BigDecimal proposedNetPosition,
            BigDecimal difference
    ) {
    }

    public record CurrencyTotal(String currency, BigDecimal grossAmount, BigDecimal residualAmount,
                                BigDecimal mutualOffsetAmount) {
    }
}
