package com.example.netting.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "netting_batch")
public class NettingBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true)
    private UUID externalId;

    @Column(name = "batch_ref", nullable = false, unique = true, length = 40)
    private String batchRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BatchStatus status = BatchStatus.TRIAL;

    @Column(name = "target_currency", length = 3)
    private String targetCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "residual_bearer", nullable = false, length = 16)
    private ResidualBearer residualBearer;

    @Column(name = "gross_payment_count", nullable = false)
    private int grossPaymentCount;

    @Column(name = "residual_payment_count", nullable = false)
    private int residualPaymentCount;

    @Column(name = "eliminated_payment_count", nullable = false)
    private int eliminatedPaymentCount;

    @Column(name = "amount_totals", nullable = false, columnDefinition = "jsonb")
    private String amountTotals;

    @Column(name = "trial_input", nullable = false, columnDefinition = "jsonb")
    private String trialInput;

    @Column(name = "input_signature", nullable = false, length = 64)
    private String inputSignature;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "sequence_no")
    private List<NettingGroup> groups = new ArrayList<>();

    @Version
    private long version;

    protected NettingBatch() {
    }

    public NettingBatch(UUID externalId, String batchRef, String targetCurrency, ResidualBearer residualBearer,
                        int grossPaymentCount, int residualPaymentCount, int eliminatedPaymentCount,
                        String amountTotals, String trialInput,
                        String inputSignature, Instant calculatedAt) {
        this.externalId = externalId;
        this.batchRef = batchRef;
        this.targetCurrency = targetCurrency;
        this.residualBearer = residualBearer;
        this.grossPaymentCount = grossPaymentCount;
        this.residualPaymentCount = residualPaymentCount;
        this.eliminatedPaymentCount = eliminatedPaymentCount;
        this.amountTotals = amountTotals;
        this.trialInput = trialInput;
        this.inputSignature = inputSignature;
        this.calculatedAt = calculatedAt;
    }

    public void addGroup(NettingGroup group) {
        groups.add(group);
        group.attachToBatch(this);
    }

    public void confirm(Instant confirmedAt) {
        if (status != BatchStatus.TRIAL) {
            throw new IllegalStateException("Only trial batches can be confirmed");
        }
        this.status = BatchStatus.CONFIRMED;
        this.confirmedAt = confirmedAt;
    }

    public Long getId() { return id; }
    public UUID getExternalId() { return externalId; }
    public String getBatchRef() { return batchRef; }
    public BatchStatus getStatus() { return status; }
    public String getTargetCurrency() { return targetCurrency; }
    public ResidualBearer getResidualBearer() { return residualBearer; }
    public int getGrossPaymentCount() { return grossPaymentCount; }
    public int getResidualPaymentCount() { return residualPaymentCount; }
    public int getEliminatedPaymentCount() { return eliminatedPaymentCount; }
    public String getAmountTotals() { return amountTotals; }
    public String getTrialInput() { return trialInput; }
    public String getInputSignature() { return inputSignature; }
    public Instant getCalculatedAt() { return calculatedAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public List<NettingGroup> getGroups() { return groups; }
    public long getVersion() { return version; }
}
