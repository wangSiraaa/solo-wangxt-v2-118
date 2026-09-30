package com.example.netting.service;

import com.example.netting.domain.AllocationType;
import com.example.netting.domain.ResidualBearer;
import com.example.netting.service.NettingModel.Allocation;
import com.example.netting.service.NettingModel.BalanceCheck;
import com.example.netting.service.NettingModel.Calculation;
import com.example.netting.service.NettingModel.ClaimRef;
import com.example.netting.service.NettingModel.EntityRef;
import com.example.netting.service.NettingModel.FxInstruction;
import com.example.netting.service.NettingModel.FxRate;
import com.example.netting.service.NettingModel.FxRateKey;
import com.example.netting.service.NettingModel.GroupResult;
import com.example.netting.service.NettingModel.Position;
import com.example.netting.service.NettingModel.Settlement;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class NettingCalculator {

    public Calculation calculate(List<ClaimRef> claims,
                                 String targetCurrency,
                                 ResidualBearer requestedBearer,
                                 Map<FxRateKey, FxRate> fxRates) {
        Map<ComponentKey, List<ClaimRef>> components = splitComponents(claims);
        List<GroupResult> groups = new ArrayList<>();
        List<BalanceCheck> checks = new ArrayList<>();
        int sequence = 1;

        for (List<ClaimRef> componentClaims : components.values()) {
            GroupResult group = calculateGroup(componentClaims, targetCurrency, requestedBearer, fxRates, sequence);
            sequence += group.settlements().size();
            groups.add(group);
            checks.addAll(balanceChecks(group));
        }

        assertBalances(checks);
        return new Calculation(groups, checks);
    }

    private Map<ComponentKey, List<ClaimRef>> splitComponents(List<ClaimRef> claims) {
        Map<Long, Long> union = new HashMap<>();
        for (ClaimRef claim : claims) {
            union.putIfAbsent(claim.debtor().id(), claim.debtor().id());
            union.putIfAbsent(claim.creditor().id(), claim.creditor().id());
            union(union, claim.debtor().id(), claim.creditor().id());
        }

        Map<ComponentKey, List<ClaimRef>> result = new LinkedHashMap<>();
        for (ClaimRef claim : claims.stream()
                .sorted(Comparator.comparing(ClaimRef::invoiceNumber).thenComparing(ClaimRef::id))
                .toList()) {
            long root = find(union, claim.debtor().id());
            result.computeIfAbsent(new ComponentKey(claim.agreement().id(), claim.currency(), root),
                    ignored -> new ArrayList<>()).add(claim);
        }
        return result;
    }

    private GroupResult calculateGroup(List<ClaimRef> claims, String targetCurrency,
                                       ResidualBearer requestedBearer, Map<FxRateKey, FxRate> fxRates,
                                       int startSequence) {
        List<ClaimRef> orderedClaims = claims.stream()
                .sorted(Comparator.comparing(ClaimRef::invoiceNumber).thenComparing(ClaimRef::id))
                .toList();

        Map<EntityRef, BigDecimal[]> positions = new LinkedHashMap<>();
        BigDecimal grossAmount = BigDecimal.ZERO;
        for (ClaimRef claim : orderedClaims) {
            positions.computeIfAbsent(claim.debtor(), ignored -> zeroPair())[0] =
                    positions.get(claim.debtor())[0].add(claim.amount());
            positions.computeIfAbsent(claim.creditor(), ignored -> zeroPair())[1] =
                    positions.get(claim.creditor())[1].add(claim.amount());
            grossAmount = grossAmount.add(claim.amount());
        }

        Map<EntityRef, BigDecimal> netPositions = new LinkedHashMap<>();
        List<Position> positionDtos = new ArrayList<>();
        List<EntityRef> payers = new ArrayList<>();
        List<EntityRef> receivers = new ArrayList<>();
        for (Map.Entry<EntityRef, BigDecimal[]> entry : positions.entrySet()) {
            BigDecimal payable = money(entry.getValue()[0]);
            BigDecimal receivable = money(entry.getValue()[1]);
            BigDecimal net = money(receivable.subtract(payable));
            netPositions.put(entry.getKey(), net);
            positionDtos.add(new Position(entry.getKey(), payable, receivable, net));
            if (net.signum() < 0) {
                payers.add(entry.getKey());
            }
            if (net.signum() > 0) {
                receivers.add(entry.getKey());
            }
        }

        Map<EntityRef, BigDecimal> payerRemaining = new LinkedHashMap<>();
        Map<EntityRef, BigDecimal> receiverRemaining = new LinkedHashMap<>();
        payers.forEach(entity -> payerRemaining.put(entity, netPositions.get(entity).abs()));
        receivers.forEach(entity -> receiverRemaining.put(entity, netPositions.get(entity)));

        List<FlowEdge> flowEdges = orderedClaims.stream().map(FlowEdge::new).toList();
        List<Settlement> settlements = new ArrayList<>();
        int payerIndex = 0;
        int receiverIndex = 0;
        int sequence = startSequence;
        BigDecimal settlementAmountTotal = BigDecimal.ZERO;

        while (payerIndex < payers.size() && receiverIndex < receivers.size()) {
            EntityRef payer = payers.get(payerIndex);
            EntityRef receiver = receivers.get(receiverIndex);
            BigDecimal amount = money(payerRemaining.get(payer).min(receiverRemaining.get(receiver)));

            List<Allocation> trace = routeResidual(flowEdges, payer, receiver, amount, sequence);
            FxInstruction fx = fxInstruction(orderedClaims.get(0).currency(), targetCurrency, amount,
                    requestedBearer, fxRates, payer, receiver);
            settlements.add(new Settlement(sequence, payer, receiver, amount, orderedClaims.get(0).currency(),
                    fx, trace));
            settlementAmountTotal = settlementAmountTotal.add(amount);

            consume(payerRemaining, payer, amount);
            consume(receiverRemaining, receiver, amount);
            if (payerRemaining.get(payer).signum() == 0) {
                payerIndex++;
            }
            if (receiverRemaining.get(receiver).signum() == 0) {
                receiverIndex++;
            }
            sequence++;
        }

        List<Allocation> groupTrace = new ArrayList<>();
        settlements.forEach(settlement -> groupTrace.addAll(settlement.allocations()));
        for (FlowEdge edge : flowEdges) {
            if (edge.remaining().signum() > 0) {
                groupTrace.add(new Allocation(edge.claim(), edge.remaining(),
                        AllocationType.MUTUAL_OFFSET, null));
            }
        }

        BigDecimal mutualOffsetAmount = money(grossAmount.subtract(settlementAmountTotal));
        return new GroupResult(
                orderedClaims.get(0).agreement(),
                orderedClaims.get(0).currency(),
                orderedClaims.size(),
                settlements.size(),
                money(grossAmount),
                mutualOffsetAmount,
                money(settlementAmountTotal),
                positionDtos,
                settlements,
                groupTrace
        );
    }

    private List<Allocation> routeResidual(List<FlowEdge> edges, EntityRef payer, EntityRef receiver,
                                           BigDecimal initialDemand, int sequence) {
        List<Allocation> allocations = new ArrayList<>();
        BigDecimal demand = initialDemand;

        while (demand.signum() > 0) {
            Map<Long, FlowEdge> predecessor = new HashMap<>();
            Deque<Long> queue = new ArrayDeque<>();
            Set<Long> visited = new HashSet<>();
            queue.add(payer.id());
            visited.add(payer.id());

            while (!queue.isEmpty() && !visited.contains(receiver.id())) {
                long currentId = queue.remove();
                for (FlowEdge edge : edges) {
                    if (edge.fromId() != currentId || edge.remaining().signum() <= 0) {
                        continue;
                    }
                    if (visited.add(edge.toId())) {
                        predecessor.put(edge.toId(), edge);
                        queue.add(edge.toId());
                    }
                }
            }

            if (!visited.contains(receiver.id())) {
                throw new IllegalStateException("No invoice path supports settlement "
                        + payer.code() + "->" + receiver.code());
            }

            List<FlowEdge> path = new ArrayList<>();
            long currentId = receiver.id();
            while (currentId != payer.id()) {
                FlowEdge edge = predecessor.get(currentId);
                path.add(0, edge);
                currentId = edge.fromId();
            }

            BigDecimal take = demand;
            for (FlowEdge edge : path) {
                take = take.min(edge.remaining());
            }
            for (int i = 0; i < path.size(); i++) {
                FlowEdge edge = path.get(i);
                edge.consume(take);
                AllocationType type = i == 0 ? AllocationType.SETTLEMENT : AllocationType.SETTLEMENT_CHAIN;
                allocations.add(new Allocation(edge.claim(), take, type, sequence));
            }
            demand = money(demand.subtract(take));
        }
        return allocations;
    }

    private FxInstruction fxInstruction(String fromCurrency, String targetCurrency, BigDecimal amount,
                                        ResidualBearer bearer, Map<FxRateKey, FxRate> fxRates,
                                        EntityRef payer, EntityRef receiver) {
        if (targetCurrency == null || fromCurrency.equalsIgnoreCase(targetCurrency)) {
            return null;
        }
        FxRate fx = fxRates.get(new FxRateKey(fromCurrency, targetCurrency));
        if (fx == null) {
            throw new IllegalArgumentException("Missing manual FX rate " + fromCurrency + "->" + targetCurrency);
        }

        BigDecimal exactAmount = amount.multiply(fx.rate());
        BigDecimal convertedAmount = exactAmount.setScale(NettingModel.FX_AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal residualAmount = convertedAmount.subtract(exactAmount)
                .abs()
                .setScale(NettingModel.FX_RESIDUAL_SCALE, RoundingMode.HALF_UP);
        ResidualBearer actualBearer = bearer == null ? ResidualBearer.PAYER : bearer;
        EntityRef residualEntity = actualBearer == ResidualBearer.PAYER ? payer : receiver;
        return new FxInstruction(fromCurrency, targetCurrency, fx.rate(), fx.rateTime(), fx.source(),
                convertedAmount, residualAmount, actualBearer, residualEntity);
    }

    private List<BalanceCheck> balanceChecks(GroupResult group) {
        Map<EntityRef, BigDecimal> proposed = new HashMap<>();
        for (Settlement settlement : group.settlements()) {
            proposed.merge(settlement.payer(), settlement.amount().negate(), BigDecimal::add);
            proposed.merge(settlement.receiver(), settlement.amount(), BigDecimal::add);
        }
        return group.positions().stream()
                .map(position -> {
                    BigDecimal proposedNet = money(proposed.getOrDefault(position.entity(), BigDecimal.ZERO));
                    BigDecimal difference = money(proposedNet.subtract(position.netPosition()));
                    return new BalanceCheck(group.agreement().code(), group.currency(), position.entity(),
                            position.netPosition(), proposedNet, difference);
                })
                .toList();
    }

    private void assertBalances(List<BalanceCheck> checks) {
        for (BalanceCheck check : checks) {
            if (check.difference().signum() != 0) {
                throw new IllegalStateException("Balance invariant broken for " + check.entity().code()
                        + " in " + check.agreementCode() + ' ' + check.currency());
            }
        }
    }

    private void consume(Map<EntityRef, BigDecimal> remaining, EntityRef entity, BigDecimal amount) {
        remaining.put(entity, money(remaining.get(entity).subtract(amount)));
    }

    private long find(Map<Long, Long> union, long id) {
        long parent = union.get(id);
        if (parent == id) {
            return id;
        }
        long root = find(union, parent);
        union.put(id, root);
        return root;
    }

    private void union(Map<Long, Long> union, long left, long right) {
        long leftRoot = find(union, left);
        long rightRoot = find(union, right);
        if (leftRoot != rightRoot) {
            union.put(Math.max(leftRoot, rightRoot), Math.min(leftRoot, rightRoot));
        }
    }

    private BigDecimal[] zeroPair() {
        return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(NettingModel.MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private record ComponentKey(long agreementId, String currency, long componentRootId) {
    }

    private static class FlowEdge {
        private final ClaimRef claim;
        private BigDecimal remaining;

        private FlowEdge(ClaimRef claim) {
            this.claim = claim;
            this.remaining = claim.amount();
        }

        private ClaimRef claim() {
            return claim;
        }

        private long fromId() {
            return claim.debtor().id();
        }

        private long toId() {
            return claim.creditor().id();
        }

        private BigDecimal remaining() {
            return remaining;
        }

        private void consume(BigDecimal amount) {
            remaining = remaining.subtract(amount).setScale(NettingModel.MONEY_SCALE, RoundingMode.HALF_UP);
        }
    }
}
