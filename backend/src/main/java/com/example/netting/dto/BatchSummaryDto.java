package com.example.netting.dto;

import com.example.netting.domain.BatchStatus;
import com.example.netting.domain.ResidualBearer;

import java.time.Instant;
import java.util.UUID;

public record BatchSummaryDto(
        UUID batchId,
        String batchRef,
        BatchStatus status,
        String targetCurrency,
        ResidualBearer residualBearer,
        int grossPaymentCount,
        int residualPaymentCount,
        int eliminatedPaymentCount,
        Instant calculatedAt,
        Instant confirmedAt
) {
}
