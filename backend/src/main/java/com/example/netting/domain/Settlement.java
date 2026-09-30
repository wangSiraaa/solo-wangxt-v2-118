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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "settlement")
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private NettingGroup group;

    @Column(name = "payer_id", nullable = false)
    private Long payerId;

    @Column(name = "payer_code", nullable = false, length = 32)
    private String payerCode;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Column(name = "receiver_code", nullable = false, length = 32)
    private String receiverCode;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "converted_amount", precision = 19, scale = 10)
    private BigDecimal convertedAmount;

    @Column(name = "fx_residual_amount", precision = 19, scale = 12)
    private BigDecimal fxResidualAmount;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "fx_quote_id", unique = true)
    private FxQuote fxQuote;

    @OneToMany(mappedBy = "settlement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ClaimAllocation> allocations = new ArrayList<>();

    protected Settlement() {
    }

    public Settlement(Long payerId, String payerCode, Long receiverId, String receiverCode, BigDecimal amount,
                      String currency, int sequenceNo, BigDecimal convertedAmount, BigDecimal fxResidualAmount,
                      FxQuote fxQuote) {
        this.payerId = payerId;
        this.payerCode = payerCode;
        this.receiverId = receiverId;
        this.receiverCode = receiverCode;
        this.amount = amount;
        this.currency = currency;
        this.sequenceNo = sequenceNo;
        this.convertedAmount = convertedAmount;
        this.fxResidualAmount = fxResidualAmount;
        this.fxQuote = fxQuote;
    }

    void attachToGroup(NettingGroup group) {
        this.group = group;
        if (fxQuote != null) {
            fxQuote.attachToGroup(group);
        }
    }

    public void addAllocation(ClaimAllocation allocation) {
        allocations.add(allocation);
        allocation.attachToSettlement(this);
    }

    public Long getId() { return id; }
    public NettingGroup getGroup() { return group; }
    public Long getPayerId() { return payerId; }
    public String getPayerCode() { return payerCode; }
    public Long getReceiverId() { return receiverId; }
    public String getReceiverCode() { return receiverCode; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public int getSequenceNo() { return sequenceNo; }
    public BigDecimal getConvertedAmount() { return convertedAmount; }
    public BigDecimal getFxResidualAmount() { return fxResidualAmount; }
    public FxQuote getFxQuote() { return fxQuote; }
    public List<ClaimAllocation> getAllocations() { return allocations; }
}
