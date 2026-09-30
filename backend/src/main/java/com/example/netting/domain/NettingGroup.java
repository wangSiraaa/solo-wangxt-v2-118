package com.example.netting.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "netting_group")
public class NettingGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private NettingBatch batch;

    @Column(name = "agreement_id", nullable = false)
    private Long agreementId;

    @Column(name = "agreement_code", nullable = false, length = 32)
    private String agreementCode;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "gross_payment_count", nullable = false)
    private int grossPaymentCount;

    @Column(name = "residual_payment_count", nullable = false)
    private int residualPaymentCount;

    @Column(name = "mutual_offset_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal mutualOffsetAmount;

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "residual_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal residualAmount;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Settlement> settlements = new ArrayList<>();

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ClaimAllocation> allocations = new ArrayList<>();

    protected NettingGroup() {
    }

    public NettingGroup(Long agreementId, String agreementCode, String currency, int grossPaymentCount,
                        int residualPaymentCount, BigDecimal mutualOffsetAmount, BigDecimal grossAmount,
                        BigDecimal residualAmount) {
        this.agreementId = agreementId;
        this.agreementCode = agreementCode;
        this.currency = currency;
        this.grossPaymentCount = grossPaymentCount;
        this.residualPaymentCount = residualPaymentCount;
        this.mutualOffsetAmount = mutualOffsetAmount;
        this.grossAmount = grossAmount;
        this.residualAmount = residualAmount;
    }

    void attachToBatch(NettingBatch batch) {
        this.batch = batch;
    }

    public void addSettlement(Settlement settlement) {
        settlements.add(settlement);
        settlement.attachToGroup(this);
    }

    public void addAllocation(ClaimAllocation allocation) {
        allocations.add(allocation);
        allocation.attachToGroup(this);
    }

    public Long getId() { return id; }
    public NettingBatch getBatch() { return batch; }
    public Long getAgreementId() { return agreementId; }
    public String getAgreementCode() { return agreementCode; }
    public String getCurrency() { return currency; }
    public int getGrossPaymentCount() { return grossPaymentCount; }
    public int getResidualPaymentCount() { return residualPaymentCount; }
    public BigDecimal getMutualOffsetAmount() { return mutualOffsetAmount; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public BigDecimal getResidualAmount() { return residualAmount; }
    public List<Settlement> getSettlements() { return settlements; }
    public List<ClaimAllocation> getAllocations() { return allocations; }
}
