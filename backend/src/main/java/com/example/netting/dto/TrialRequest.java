package com.example.netting.dto;

import com.example.netting.domain.ResidualBearer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TrialRequest(
        String agreementCode,
        @Size(min = 3, max = 3) String currency,
        @Size(min = 3, max = 3) String targetCurrency,
        Instant fxRateTime,
        @NotNull ResidualBearer residualBearer,
        @Valid List<FxRateInput> fxRates
) {

    public record FxRateInput(
            @NotNull @Size(min = 3, max = 3) String fromCurrency,
            @NotNull @Size(min = 3, max = 3) String toCurrency,
            @NotNull @Positive BigDecimal rate
    ) {
    }
}
