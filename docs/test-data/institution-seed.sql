-- Hangang Pay institution seed data for local integration tests.
--
-- Run this script against BOTH databases:
--   1. hangang-pay-be database
--   2. hangang-pay-bank database
--
-- The institution IDs must match across BE, Bank, and blockchain contracts.
-- LocalCurrencyPolicy.sol fixes Woori Bank as institution id 2.

INSERT INTO institution (id, institution_code, institution_name, created_at, updated_at)
VALUES
    (1, 'BoK', '한국은행', NOW(), NOW()),
    (2, 'WR', '우리은행', NOW(), NOW()),
    (3, 'SH', '신한은행', NOW(), NOW()),
    (4, 'HN', '하나은행', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    institution_code = VALUES(institution_code),
    institution_name = VALUES(institution_name),
    updated_at = VALUES(updated_at);

SELECT id, institution_code, institution_name
FROM institution
ORDER BY id;
