package com.example.netting.service;

import com.example.netting.domain.AllocationType;
import com.example.netting.domain.ResidualBearer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class NettingModel {

    public static final int MONEY_SCALE = 4;
    public static final int FX_AMOUNT_SCALE = 10;
    public static final int FX_RESIDUAL_SCALE = 12;

    private NettingModel() {
    }

    public record EntityRef(long id, String code, String name) {
    }

    public record AgreementRef(long id, String code, String name) {
    }

    public record ClaimRef(
            long id,
            String invoiceNumber,
            EntityRef creditor,
            EntityRef debtor,
            AgreementRef agreement,
            BigDecimal amount,
            String currency,
            boolean pledged,
            boolean disputed
    ) {
    }

    public record FxRateKey(String fromCurrency, String toCurrency) {
    }

    public record FxRate(BigDecimal rate, Instant rateTime, String source) {
    }

    public record FxInstruction(
            String fromCurrency,
            String toCurrency,
            BigDecimal rate,
            Instant rateTime,
            String source,
            BigDecimal convertedAmount,
            BigDecimal residualAmount,
            ResidualBearer residualBearer,
            EntityRef residualEntity
    ) {
    }

    public record Allocation(
            ClaimRef claim,
            BigDecimal allocatedAmount,
            AllocationType type,
            Integer settlementSequence
    ) {
    }

    public record Settlement(
            int sequence,
            EntityRef payer,
            EntityRef receiver,
            BigDecimal amount,
            String currency,
            FxInstruction fx,
            List<Allocation> allocations
    ) {
    }

    public record Position(EntityRef entity, BigDecimal payable, BigDecimal receivable, BigDecimal netPosition) {
    }

    public record GroupResult(
            AgreementRef agreement,
            String currency,
            int grossPaymentCount,
            int residualPaymentCount,
            BigDecimal grossAmount,
            BigDecimal mutualOffsetAmount,
            BigDecimal residualAmount,
            List<Position> positions,
            List<Settlement> settlements,
            List<Allocation> trace
    ) {
    }

    public record BalanceCheck(
            String agreementCode,
            String currency,
            EntityRef entity,
            BigDecimal originalNetPosition,
            BigDecimal proposedNetPosition,
            BigDecimal difference
    ) {
    }

    public record Calculation(
            List<GroupResult> groups,
            List<BalanceCheck> balanceChecks
    ) {
    }
}
