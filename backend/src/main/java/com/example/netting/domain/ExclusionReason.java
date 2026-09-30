package com.example.netting.domain;

public enum ExclusionReason {
    MISSING_AGREEMENT,
    AGREEMENT_SCOPE_FILTERED,
    CURRENCY_FILTERED,
    NOT_PARTY_TO_AGREEMENT,
    AGREEMENT_NOT_IN_EFFECT,
    PLEDGED,
    DISPUTED,
    NOT_OPEN
}
