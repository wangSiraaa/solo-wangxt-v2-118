CREATE TABLE legal_entity (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL
);

CREATE TABLE netting_agreement (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ
);

CREATE TABLE agreement_party (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agreement_id BIGINT NOT NULL REFERENCES netting_agreement(id),
    party_id BIGINT NOT NULL REFERENCES legal_entity(id),
    UNIQUE (agreement_id, party_id)
);

CREATE TABLE claim (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_number VARCHAR(64) NOT NULL UNIQUE,
    creditor_id BIGINT NOT NULL REFERENCES legal_entity(id),
    debtor_id BIGINT NOT NULL REFERENCES legal_entity(id),
    agreement_id BIGINT REFERENCES netting_agreement(id),
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    invoice_date DATE NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('OPEN', 'NETTED', 'SETTLED')),
    pledged BOOLEAN NOT NULL DEFAULT FALSE,
    disputed BOOLEAN NOT NULL DEFAULT FALSE,
    description VARCHAR(300),
    claimed_at TIMESTAMPTZ NOT NULL,
    CHECK (creditor_id <> debtor_id),
    CHECK (due_date >= invoice_date)
);

CREATE INDEX idx_claim_agreement_currency ON claim(agreement_id, currency);
CREATE INDEX idx_claim_status ON claim(status);

CREATE TABLE netting_batch (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE,
    batch_ref VARCHAR(40) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('TRIAL', 'CONFIRMED', 'CANCELLED')),
    target_currency CHAR(3),
    residual_bearer VARCHAR(16) NOT NULL CHECK (residual_bearer IN ('PAYER', 'RECEIVER')),
    gross_payment_count INTEGER NOT NULL CHECK (gross_payment_count >= 0),
    residual_payment_count INTEGER NOT NULL CHECK (residual_payment_count >= 0),
    eliminated_payment_count INTEGER NOT NULL CHECK (eliminated_payment_count >= 0),
    amount_totals JSONB NOT NULL,
    trial_input JSONB NOT NULL,
    input_signature CHAR(64) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE netting_group (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES netting_batch(id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL,
    agreement_id BIGINT NOT NULL,
    agreement_code VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL,
    gross_payment_count INTEGER NOT NULL,
    residual_payment_count INTEGER NOT NULL,
    mutual_offset_amount NUMERIC(19, 4) NOT NULL,
    gross_amount NUMERIC(19, 4) NOT NULL,
    residual_amount NUMERIC(19, 4) NOT NULL,
    UNIQUE (batch_id, sequence_no)
);

CREATE TABLE fx_quote (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES netting_group(id) ON DELETE CASCADE,
    from_currency CHAR(3) NOT NULL,
    to_currency CHAR(3) NOT NULL,
    rate NUMERIC(28, 12) NOT NULL CHECK (rate > 0),
    rate_time TIMESTAMPTZ NOT NULL,
    source VARCHAR(40) NOT NULL,
    residual_bearer VARCHAR(16) NOT NULL CHECK (residual_bearer IN ('PAYER', 'RECEIVER')),
    residual_amount NUMERIC(19, 12) NOT NULL,
    residual_entity_id BIGINT NOT NULL,
    residual_entity_code VARCHAR(32) NOT NULL
);

CREATE TABLE settlement (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES netting_group(id) ON DELETE CASCADE,
    payer_id BIGINT NOT NULL,
    payer_code VARCHAR(32) NOT NULL,
    receiver_id BIGINT NOT NULL,
    receiver_code VARCHAR(32) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    sequence_no INTEGER NOT NULL UNIQUE,
    converted_amount NUMERIC(19, 10),
    fx_residual_amount NUMERIC(19, 12),
    fx_quote_id BIGINT REFERENCES fx_quote(id)
);

CREATE TABLE claim_allocation (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES netting_group(id) ON DELETE CASCADE,
    settlement_id BIGINT,
    claim_id BIGINT NOT NULL,
    invoice_number VARCHAR(64) NOT NULL,
    debtor_id BIGINT NOT NULL,
    creditor_id BIGINT NOT NULL,
    allocation_type VARCHAR(20) NOT NULL CHECK (allocation_type IN ('MUTUAL_OFFSET', 'SETTLEMENT', 'SETTLEMENT_CHAIN')),
    allocated_amount NUMERIC(19, 4) NOT NULL CHECK (allocated_amount >= 0),
    CHECK ((settlement_id IS NOT NULL AND allocation_type <> 'MUTUAL_OFFSET')
        OR (settlement_id IS NULL AND allocation_type = 'MUTUAL_OFFSET'))
);

CREATE INDEX idx_claim_allocation_claim ON claim_allocation(claim_id);
CREATE INDEX idx_claim_allocation_settlement ON claim_allocation(settlement_id);
