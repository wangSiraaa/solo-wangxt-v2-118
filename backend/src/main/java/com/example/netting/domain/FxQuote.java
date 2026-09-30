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
import java.time.Instant;

@Entity
@Table(name = "fx_quote")
public class FxQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private NettingGroup group;

    @Column(name = "from_currency", nullable = false, length = 3)
    private String fromCurrency;

    @Column(name = "to_currency", nullable = false, length = 3)
    private String toCurrency;

    @Column(name = "rate", nullable = false, precision = 28, scale = 12)
    private BigDecimal rate;

    @Column(name = "rate_time", nullable = false)
    private Instant rateTime;

    @Column(name = "source", nullable = false, length = 40)
    private String source;

    @Column(name = "residual_bearer", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private ResidualBearer residualBearer;

    @Column(name = "residual_amount", nullable = false, precision = 19, scale = 12)
    private BigDecimal residualAmount;

    @Column(name = "residual_entity_id", nullable = false)
    private Long residualEntityId;

    @Column(name = "residual_entity_code", nullable = false, length = 32)
    private String residualEntityCode;

    protected FxQuote() {
    }

    public FxQuote(String fromCurrency, String toCurrency, BigDecimal rate, Instant rateTime, String source,
                   ResidualBearer residualBearer, BigDecimal residualAmount, Long residualEntityId,
                   String residualEntityCode) {
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
        this.rate = rate;
        this.rateTime = rateTime;
        this.source = source;
        this.residualBearer = residualBearer;
        this.residualAmount = residualAmount;
        this.residualEntityId = residualEntityId;
        this.residualEntityCode = residualEntityCode;
    }

    void attachToGroup(NettingGroup group) {
        this.group = group;
    }

    public Long getId() { return id; }
    public NettingGroup getGroup() { return group; }
    public String getFromCurrency() { return fromCurrency; }
    public String getToCurrency() { return toCurrency; }
    public BigDecimal getRate() { return rate; }
    public Instant getRateTime() { return rateTime; }
    public String getSource() { return source; }
    public ResidualBearer getResidualBearer() { return residualBearer; }
    public BigDecimal getResidualAmount() { return residualAmount; }
    public Long getResidualEntityId() { return residualEntityId; }
    public String getResidualEntityCode() { return residualEntityCode; }
}
