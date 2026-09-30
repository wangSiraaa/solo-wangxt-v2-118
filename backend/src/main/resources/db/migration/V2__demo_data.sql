INSERT INTO legal_entity (id, code, name) VALUES
    (1, 'CN-A', '甲法人'),
    (2, 'CN-B', '乙法人'),
    (3, 'CN-C', '丙法人'),
    (4, 'CN-D', '丁法人');

SELECT setval(pg_get_serial_sequence('legal_entity', 'id'), 4);

INSERT INTO netting_agreement (id, code, name, effective_from, effective_to) VALUES
    (1, 'NA-TRI', '三方内部债权抵销协议', '2026-01-01T00:00:00Z', NULL),
    (2, 'NA-BILAT', '仅甲乙双边抵销协议', '2026-01-01T00:00:00Z', NULL);

SELECT setval(pg_get_serial_sequence('netting_agreement', 'id'), 2);

INSERT INTO agreement_party (agreement_id, party_id) VALUES
    (1, 1), (1, 2), (1, 3),
    (2, 1), (2, 2);

INSERT INTO claim (
    invoice_number, creditor_id, debtor_id, agreement_id, amount, currency,
    invoice_date, due_date, status, pledged, disputed, description, claimed_at
) VALUES
    -- Classic ring: 甲 owes 乙, 乙 owes 丙, 丙 owes 甲.
    ('INV-B-1001', 2, 1, 1, 100.0000, 'CNY', DATE '2026-09-01', DATE '2026-10-01', 'OPEN', FALSE, FALSE, '甲向乙采购服务', '2026-09-01T08:00:00Z'),
    ('INV-C-1002', 3, 2, 1, 100.0000, 'CNY', DATE '2026-09-02', DATE '2026-10-02', 'OPEN', FALSE, FALSE, '乙向丙采购材料', '2026-09-02T08:00:00Z'),
    ('INV-A-1003', 1, 3, 1, 100.0000, 'CNY', DATE '2026-09-03', DATE '2026-10-03', 'OPEN', FALSE, FALSE, '丙向甲采购设备', '2026-09-03T08:00:00Z'),

    -- Unequal ring with a small amount and four-decimal residual.
    ('INV-B-1101', 2, 1, 1, 100.2500, 'CNY', DATE '2026-09-05', DATE '2026-10-05', 'OPEN', FALSE, FALSE, '不等额环：甲欠乙', '2026-09-05T08:00:00Z'),
    ('INV-C-1102', 3, 2, 1, 80.5000, 'CNY', DATE '2026-09-06', DATE '2026-10-06', 'OPEN', FALSE, FALSE, '不等额环：乙欠丙', '2026-09-06T08:00:00Z'),
    ('INV-A-1103', 1, 3, 1, 60.7500, 'CNY', DATE '2026-09-07', DATE '2026-10-07', 'OPEN', FALSE, FALSE, '不等额环：丙欠甲', '2026-09-07T08:00:00Z'),

    -- Eligible cross-currency group, never mixed with CNY.
    ('INV-B-2001', 2, 1, 1, 100.0000, 'EUR', DATE '2026-09-10', DATE '2026-10-10', 'OPEN', FALSE, FALSE, '欧元：甲欠乙', '2026-09-10T08:00:00Z'),
    ('INV-C-2002', 3, 2, 1, 70.0000, 'EUR', DATE '2026-09-11', DATE '2026-10-11', 'OPEN', FALSE, FALSE, '欧元：乙欠丙', '2026-09-11T08:00:00Z'),
    ('INV-A-2003', 1, 3, 1, 40.0000, 'EUR', DATE '2026-09-12', DATE '2026-10-12', 'OPEN', FALSE, FALSE, '欧元：丙欠甲', '2026-09-12T08:00:00Z'),

    -- Excluded because pledged or disputed.
    ('INV-X-3001', 2, 1, 1, 999.0000, 'CNY', DATE '2026-09-15', DATE '2026-10-15', 'OPEN', TRUE, FALSE, '已质押债权必须排除', '2026-09-15T08:00:00Z'),
    ('INV-X-3002', 3, 2, 1, 888.0000, 'CNY', DATE '2026-09-16', DATE '2026-10-16', 'OPEN', FALSE, TRUE, '争议债权必须排除', '2026-09-16T08:00:00Z'),

    -- Excluded because 丁 is not a party to the trilateral agreement.
    ('INV-D-4001', 4, 1, 1, 777.0000, 'CNY', DATE '2026-09-17', DATE '2026-10-17', 'OPEN', FALSE, FALSE, '协议边界外：丁不在协议内', '2026-09-17T08:00:00Z'),

    -- Excluded because no netting agreement exists.
    ('INV-X-5001', 1, 2, NULL, 666.0000, 'CNY', DATE '2026-09-18', DATE '2026-10-18', 'OPEN', FALSE, FALSE, '无互抵协议，保留原债务', '2026-09-18T08:00:00Z'),

    -- Eligible only under a separate bilateral agreement and not mixed with trilateral claims.
    ('INV-B-6001', 2, 1, 2, 50.0000, 'USD', DATE '2026-09-20', DATE '2026-10-20', 'OPEN', FALSE, FALSE, '双边协议：甲欠乙美元', '2026-09-20T08:00:00Z'),
    ('INV-A-6002', 1, 2, 2, 20.0000, 'USD', DATE '2026-09-21', DATE '2026-10-21', 'OPEN', FALSE, FALSE, '双边协议：乙欠甲美元', '2026-09-21T08:00:00Z');
