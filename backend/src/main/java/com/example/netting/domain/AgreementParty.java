package com.example.netting.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "agreement_party",
        uniqueConstraints = @UniqueConstraint(columnNames = {"agreement_id", "party_id"})
)
public class AgreementParty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agreement_id", nullable = false)
    private NettingAgreement agreement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false)
    private LegalEntity party;

    protected AgreementParty() {
    }

    public AgreementParty(NettingAgreement agreement, LegalEntity party) {
        this.agreement = agreement;
        this.party = party;
    }

    public Long getId() {
        return id;
    }

    public NettingAgreement getAgreement() {
        return agreement;
    }

    public LegalEntity getParty() {
        return party;
    }
}
