package com.example.netting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "claim_allocation")
public class ClaimAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private NettingGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id")
    private Settlement settlement;

    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    @Column(name = "invoice_number", nullable = false, length = 64)
    private String invoiceNumber;

    @Column(name = "debtor_id", nullable = false)
    private Long debtorId;

    @Column(name = "creditor_id", nullable = false)
    private Long creditorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_type", nullable = false, length = 20)
    private AllocationType allocationType;

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedAmount;

    protected ClaimAllocation() {
    }

    public ClaimAllocation(Long claimId, String invoiceNumber, Long debtorId, Long creditorId,
                           AllocationType allocationType, BigDecimal allocatedAmount) {
        this.claimId = claimId;
        this.invoiceNumber = invoiceNumber;
        this.debtorId = debtorId;
        this.creditorId = creditorId;
        this.allocationType = allocationType;
        this.allocatedAmount = allocatedAmount;
    }

    void attachToGroup(NettingGroup group) {
        this.group = group;
    }

    void attachToSettlement(Settlement settlement) {
        this.settlement = settlement;
    }

    public Long getId() { return id; }
    public NettingGroup getGroup() { return group; }
    public Settlement getSettlement() { return settlement; }
    public Long getClaimId() { return claimId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public Long getDebtorId() { return debtorId; }
    public Long getCreditorId() { return creditorId; }
    public AllocationType getAllocationType() { return allocationType; }
    public BigDecimal getAllocatedAmount() { return allocatedAmount; }
}
