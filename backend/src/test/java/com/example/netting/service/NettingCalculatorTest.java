package com.example.netting.service;

import com.example.netting.domain.AllocationType;
import com.example.netting.domain.ResidualBearer;
import com.example.netting.service.NettingModel.AgreementRef;
import com.example.netting.service.NettingModel.Calculation;
import com.example.netting.service.NettingModel.ClaimRef;
import com.example.netting.service.NettingModel.EntityRef;
import com.example.netting.service.NettingModel.FxRate;
import com.example.netting.service.NettingModel.FxRateKey;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NettingCalculatorTest {

    private static final EntityRef A = new NettingModel.EntityRef(1, "CN-A", "甲法人");
    private static final EntityRef B = new NettingModel.EntityRef(2, "CN-B", "乙法人");
    private static final EntityRef C = new NettingModel.EntityRef(3, "CN-C", "丙法人");
    private static final AgreementRef TRI = new AgreementRef(1, "NA-TRI", "三方抵销协议");

    @Test
    void equalThreePartyRingEliminatesAllThreePaymentsAndKeepsNetPositionsAtZero() {
        List<ClaimRef> claims = List.of(
                claim("INV-B-1001", B, A, "100.00", "CNY"),
                claim("INV-C-1002", C, B, "100.00", "CNY"),
                claim("INV-A-1003", A, C, "100.00", "CNY")
        );

        Calculation result = new NettingCalculator().calculate(claims, null, ResidualBearer.PAYER, Map.of());

        assertThat(result.groups()).hasSize(1);
        assertThat(result.groups().get(0).grossPaymentCount()).isEqualTo(3);
        assertThat(result.groups().get(0).residualPaymentCount()).isZero();
        assertThat(result.groups().get(0).mutualOffsetAmount()).isEqualByComparingTo("300.0000");
        assertThat(result.groups().get(0).residualAmount()).isEqualByComparingTo("0.0000");
        assertThat(result.groups().get(0).trace())
                .extracting(NettingModel.Allocation::type)
                .containsOnly(AllocationType.MUTUAL_OFFSET);
        assertThat(result.balanceChecks())
                .allSatisfy(check -> assertThat(check.difference()).isEqualByComparingTo("0.0000"));
    }

    @Test
    void unequalRingLeavesOnlyNetResidualPaymentsAndAllocatesEveryOriginalInvoiceFully() {
        List<ClaimRef> claims = List.of(
                claim("INV-B-1101", B, A, "100.25", "CNY"),
                claim("INV-C-1102", C, B, "80.50", "CNY"),
                claim("INV-A-1103", A, C, "60.75", "CNY")
        );

        Calculation result = new NettingCalculator().calculate(claims, null, ResidualBearer.PAYER, Map.of());
        NettingModel.GroupResult group = result.groups().get(0);

        assertThat(group.grossPaymentCount()).isEqualTo(3);
        assertThat(group.residualPaymentCount()).isEqualTo(2);
        assertThat(group.settlements()).extracting(NettingModel.Settlement::amount)
                .containsExactly(new BigDecimal("19.7500"), new BigDecimal("19.7500"));
        assertThat(group.residualAmount()).isEqualByComparingTo("39.5000");
        assertThat(group.mutualOffsetAmount()).isEqualByComparingTo("202.0000");
        assertThat(sumAllocationsByInvoice(group.trace())).allSatisfy((invoice, amount) ->
                assertThat(amount).isEqualByComparingTo(
                        switch (invoice) {
                            case "INV-A-1103" -> "60.7500";
                            case "INV-B-1101" -> "100.2500";
                            default -> "80.5000";
                        }));
        assertThat(result.balanceChecks())
                .allSatisfy(check -> assertThat(check.difference()).isEqualByComparingTo("0.0000"));
    }

    @Test
    void differentCurrenciesRemainSeparateGroupsAndDoNotChangeEitherCurrencyNetPosition() {
        List<ClaimRef> claims = List.of(
                claim("CNY-1", B, A, "100.00", "CNY"),
                claim("CNY-2", C, B, "100.00", "CNY"),
                claim("CNY-3", A, C, "100.00", "CNY"),
                claim("EUR-1", B, A, "100.00", "EUR"),
                claim("EUR-2", C, B, "70.00", "EUR"),
                claim("EUR-3", A, C, "40.00", "EUR")
        );

        Calculation result = new NettingCalculator().calculate(claims, null, ResidualBearer.PAYER, Map.of());

        assertThat(result.groups()).extracting(NettingModel.GroupResult::currency)
                .containsExactly("CNY", "EUR");
        assertThat(result.groups()).filteredOn(group -> group.currency().equals("CNY"))
                .singleElement()
                .satisfies(group -> assertThat(group.residualPaymentCount()).isZero());
        assertThat(result.groups()).filteredOn(group -> group.currency().equals("EUR"))
                .singleElement()
                .satisfies(group -> {
                    assertThat(group.residualPaymentCount()).isEqualTo(2);
                    assertThat(group.residualAmount()).isEqualByComparingTo("60.0000");
                });
        assertThat(result.balanceChecks())
                .allSatisfy(check -> assertThat(check.difference()).isEqualByComparingTo("0.0000"));
    }

    @Test
    void crossCurrencyDisplayUsesManualRateRateTimeAndExplicitRoundingResidual() {
        List<ClaimRef> claims = List.of(
                claim("EUR-1", B, A, "10.00", "EUR"),
                claim("EUR-2", C, B, "7.00", "EUR"),
                claim("EUR-3", A, C, "4.00", "EUR")
        );
        Instant rateTime = Instant.parse("2026-09-30T09:30:00Z");
        FxRateKey key = new FxRateKey("EUR", "CNY");
        FxRate rate = new FxRate(new BigDecimal("7.81234567895"), rateTime, "MANUAL_TRIAL_INPUT");

        Calculation result = new NettingCalculator().calculate(claims, "CNY", ResidualBearer.RECEIVER,
                Map.of(key, rate));

        NettingModel.FxInstruction fx = result.groups().get(0).settlements().get(0).fx();
        assertThat(fx).isNotNull();
        assertThat(fx.fromCurrency()).isEqualTo("EUR");
        assertThat(fx.toCurrency()).isEqualTo("CNY");
        assertThat(fx.rateTime()).isEqualTo(rateTime);
        assertThat(fx.convertedAmount()).isEqualByComparingTo("23.4370370369");
        assertThat(fx.residualAmount()).isEqualByComparingTo("0.00000000005");
        assertThat(fx.residualBearer()).isEqualTo(ResidualBearer.RECEIVER);
        assertThat(result.balanceChecks())
                .allSatisfy(check -> assertThat(check.difference()).isEqualByComparingTo("0.0000"));
    }

    private ClaimRef claim(String invoice, EntityRef creditor, EntityRef debtor, String amount, String currency) {
        return new ClaimRef(Long.parseLong(invoice.replaceAll("\\D", "")), invoice, creditor, debtor, TRI,
                new BigDecimal(amount), currency, false, false);
    }

    private Map<String, BigDecimal> sumAllocationsByInvoice(List<NettingModel.Allocation> allocations) {
        return allocations.stream().collect(java.util.stream.Collectors.groupingBy(
                allocation -> allocation.claim().invoiceNumber(),
                java.util.stream.Collectors.mapping(NettingModel.Allocation::allocatedAmount,
                        java.util.stream.Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
    }
}
