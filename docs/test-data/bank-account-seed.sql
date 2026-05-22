-- Hangang Pay bank_account seed data for local integration tests.
--
-- Run this script against the hangang-pay-bank database only.
-- Run docs/test-data/institution-seed.sql first so institution ids 1..4 exist.

INSERT INTO bank_account (
    institution_id,
    account_number,
    owner_name,
    balance,
    created_at,
    updated_at
)
SELECT 2, '222000000001', '홍길동', 1000000.0000, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM bank_account WHERE institution_id = 2 AND account_number = '222000000001'
);

INSERT INTO bank_account (
    institution_id,
    account_number,
    owner_name,
    balance,
    created_at,
    updated_at
)
SELECT 2, '222000000002', '김한강', 500000.0000, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM bank_account WHERE institution_id = 2 AND account_number = '222000000002'
);

INSERT INTO bank_account (
    institution_id,
    account_number,
    owner_name,
    balance,
    created_at,
    updated_at
)
SELECT 3, '333000000001', '이성수', 750000.0000, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM bank_account WHERE institution_id = 3 AND account_number = '333000000001'
);

INSERT INTO bank_account (
    institution_id,
    account_number,
    owner_name,
    balance,
    created_at,
    updated_at
)
SELECT 4, '444000000001', '박서울', 750000.0000, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM bank_account WHERE institution_id = 4 AND account_number = '444000000001'
);

SELECT
    ba.id,
    ba.institution_id,
    i.institution_code,
    i.institution_name,
    ba.account_number,
    ba.owner_name,
    ba.balance
FROM bank_account ba
JOIN institution i ON i.id = ba.institution_id
WHERE ba.account_number IN (
    '222000000001',
    '222000000002',
    '333000000001',
    '444000000001'
)
ORDER BY ba.institution_id, ba.account_number;
