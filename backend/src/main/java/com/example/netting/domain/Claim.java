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
import java.time.LocalDate;

@Entity
@Table(name = "claim")
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 64)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creditor_id", nullable = false)
    private LegalEntity creditor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debtor_id", nullable = false)
    private LegalEntity debtor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agreement_id")
    private NettingAgreement agreement;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClaimStatus status = ClaimStatus.OPEN;

    @Column(nullable = false)
    private boolean pledged = false;

    @Column(nullable = false)
    private boolean disputed = false;

    @Column(length = 300)
    private String description;

    @Column(name = "claimed_at", nullable = false)
    private java.time.Instant claimedAt;

    protected Claim() {
    }

    public Claim(String invoiceNumber, LegalEntity creditor, LegalEntity debtor, NettingAgreement agreement,
                 BigDecimal amount, String currency, LocalDate invoiceDate, LocalDate dueDate,
                 ClaimStatus status, boolean pledged, boolean disputed, String description,
                 java.time.Instant claimedAt) {
        this.invoiceNumber = invoiceNumber;
        this.creditor = creditor;
        this.debtor = debtor;
        this.agreement = agreement;
        this.amount = amount;
        this.currency = currency;
        this.invoiceDate = invoiceDate;
        this.dueDate = dueDate;
        this.status = status;
        this.pledged = pledged;
        this.disputed = disputed;
        this.description = description;
        this.claimedAt = claimedAt;
    }

    public Long getId() { return id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public LegalEntity getCreditor() { return creditor; }
    public LegalEntity getDebtor() { return debtor; }
    public NettingAgreement getAgreement() { return agreement; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public LocalDate getDueDate() { return dueDate; }
    public ClaimStatus getStatus() { return status; }
    public boolean isPledged() { return pledged; }
    public boolean isDisputed() { return disputed; }
    public String getDescription() { return description; }
    public java.time.Instant getClaimedAt() { return claimedAt; }

    public void markNetted() {
        if (status != ClaimStatus.OPEN) {
            throw new IllegalStateException("Only open claims can be netted: " + invoiceNumber);
        }
        this.status = ClaimStatus.NETTED;
    }
}
