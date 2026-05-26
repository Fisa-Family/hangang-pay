-- DB 상태 설정 및 확인 쿼리
-- 로그인 기능 미구현 상태에서 FE 개발 및 테스트에 필요한 데이터 삽입과 조회 쿼리
--
-- hangang_pay      : BE 메인 DB
-- hangang_pay_bank : 은행 서버 DB (직접 JOIN 불가)
--
-- 실행:
--   mysql --default-character-set=utf8mb4 -u root -p1234 < docs/test-data/check-state.sql
--
-- --default-character-set=utf8mb4 없이 실행하면 한글에서 ERROR 1366 발생
-- FK 순서: institution → party → users/merchant → wallet → account → transaction


-- ============================================================
-- hangang_pay
-- ============================================================
USE hangang_pay;

-- institution: 양쪽 DB의 id가 일치해야 한다
INSERT INTO institution (id, institution_code, institution_name, created_at, updated_at)
VALUES
    (1, 'BoK', '한국은행', NOW(), NOW()),
    (2, 'WR',  '우리은행', NOW(), NOW()),
    (3, 'SH',  '신한은행', NOW(), NOW()),
    (4, 'HN',  '하나은행', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    institution_code = VALUES(institution_code),
    institution_name = VALUES(institution_name),
    updated_at       = VALUES(updated_at);

SELECT id, institution_code, institution_name FROM institution ORDER BY id;

-- party: id=1001(USER), id=1002(MERCHANT)
INSERT INTO party (id, party_type, created_at, updated_at)
VALUES
    (1001, 'USER',     NOW(), NOW()),
    (1002, 'MERCHANT', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    party_type = VALUES(party_type),
    updated_at = VALUES(updated_at);

SELECT id, party_type FROM party ORDER BY id;

-- users: phone=01012345678 / password=test1234
INSERT INTO users (id, party_id, username, phone_number, password_hash, payment_pin_hash, created_at, updated_at)
VALUES (
    1001, 1001, '김한강', '01012345678',
    '$2a$10$qmDX8c0r05cKVxsaPgI/SeL7TjY.Upi8D8jY.5uIv1TlQ7cTBUeeK',
    '$2a$10$2YHi/NCsIBtA4yMGOrM/sObhTn6UGpJ003zuBBb6yOcZGx36T/7ii',
    NOW(), NOW()
)
ON DUPLICATE KEY UPDATE
    party_id         = VALUES(party_id),
    username         = VALUES(username),
    phone_number     = VALUES(phone_number),
    password_hash    = VALUES(password_hash),
    payment_pin_hash = VALUES(payment_pin_hash),
    updated_at       = VALUES(updated_at);

SELECT id, party_id, username, phone_number, LEFT(password_hash, 20) AS hash_prefix
FROM users ORDER BY id;

-- merchant: phone=01087654321 / password=test1234
-- hash_prefix 가 '$2a$10$qmDX8c0r0' 이어야 FE 자동 로그인 성공
INSERT INTO merchant (
    id, party_id, username, merchant_name, owner_name,
    phone_number, business_number, address,
    password_hash, created_at, updated_at
)
VALUES (
    1001, 1002, 'local-merchant', '한강상점', '홍길동',
    '01087654321', '123-45-67890', 'Seoul',
    '$2a$10$qmDX8c0r05cKVxsaPgI/SeL7TjY.Upi8D8jY.5uIv1TlQ7cTBUeeK',
    NOW(), NOW()
)
ON DUPLICATE KEY UPDATE
    party_id        = VALUES(party_id),
    username        = VALUES(username),
    merchant_name   = VALUES(merchant_name),
    owner_name      = VALUES(owner_name),
    phone_number    = VALUES(phone_number),
    business_number = VALUES(business_number),
    address         = VALUES(address),
    password_hash   = VALUES(password_hash),
    updated_at      = VALUES(updated_at);

SELECT id, party_id, merchant_name, phone_number, LEFT(password_hash, 20) AS hash_prefix
FROM merchant ORDER BY id;

-- wallet: 블록체인 지갑 주소
INSERT INTO wallet (id, party_id, institution_id, address, created_at, updated_at)
VALUES
    (1001, 1001, 2, '0x1111111111111111111111111111111111111111', NOW(), NOW()),
    (1002, 1002, 2, '0x2222222222222222222222222222222222222222', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    party_id       = VALUES(party_id),
    institution_id = VALUES(institution_id),
    address        = VALUES(address),
    updated_at     = VALUES(updated_at);

SELECT id, party_id, institution_id, address FROM wallet ORDER BY id;

-- account: 등록된 은행 계좌 참조 / party_id=1002 SETTLEMENT 이 가맹점 정산 계좌
INSERT INTO account (id, party_id, institution_id, account_number, account_type, created_at, updated_at)
VALUES
    (1001, 1001, 2, '20012345678',  'PRIMARY',    NOW(), NOW()),
    (1002, 1002, 2, '20098765432',  'SETTLEMENT', NOW(), NOW()),
    (1007, 1001, 2, '222000000001', 'SECONDARY',  NOW(), NOW()),
    (1010, 1002, 2, '222000000001', 'SECONDARY',  NOW(), NOW())
ON DUPLICATE KEY UPDATE
    party_id       = VALUES(party_id),
    institution_id = VALUES(institution_id),
    account_number = VALUES(account_number),
    account_type   = VALUES(account_type),
    updated_at     = VALUES(updated_at);

SELECT id, party_id, institution_id, account_number, account_type FROM account ORDER BY id;

-- transaction: EXCHANGE(정산) from_party_id=1002 이 FE 가맹점 홈에 표시됨
INSERT INTO transaction (
    id, transaction_uuid, transaction_type, status,
    amount, discount_amount, discount_rate,
    from_party_id, to_party_id,
    from_wallet_id, to_wallet_id,
    from_account_id, to_account_id,
    reconcile_attempt_count, created_at, updated_at
)
VALUES
    -- 충전 1건
    (10006, '550e8400-e29b-41d4-a716-446655440006', 'CHARGE', 'SUCCESS',
     100000.00, 10000.00, 10.00, 1001, NULL, NULL, 1001, NULL, NULL,
     0, '2026-05-26 03:57:57.849422', '2026-05-26 03:57:57.849422'),
    -- 결제 5건 (사용자→가맹점)
    (10001, '550e8400-e29b-41d4-a716-446655440001', 'PAYMENT', 'SUCCESS',
      8500.00, NULL, NULL, 1001, 1002, 1001, 1002, NULL, NULL,
     0, '2026-05-26 04:57:57.849422', '2026-05-26 04:57:57.849422'),
    (10002, '550e8400-e29b-41d4-a716-446655440002', 'PAYMENT', 'SUCCESS',
     12000.00, NULL, NULL, 1001, 1002, 1001, 1002, NULL, NULL,
     0, '2026-05-26 06:57:57.849422', '2026-05-26 06:57:57.849422'),
    (10003, '550e8400-e29b-41d4-a716-446655440003', 'PAYMENT', 'SUCCESS',
     25000.00, NULL, NULL, 1001, 1002, 1001, 1002, NULL, NULL,
     0, '2026-05-26 07:57:57.849422', '2026-05-26 07:57:57.849422'),
    (10004, '550e8400-e29b-41d4-a716-446655440004', 'PAYMENT', 'SUCCESS',
      5000.00, NULL, NULL, 1001, 1002, 1001, 1002, NULL, NULL,
     0, '2026-05-26 09:57:57.849422', '2026-05-26 09:57:57.849422'),
    (10005, '550e8400-e29b-41d4-a716-446655440005', 'PAYMENT', 'SUCCESS',
     18000.00, NULL, NULL, 1001, 1002, 1001, 1002, NULL, NULL,
     0, '2026-05-25 05:57:57.849422', '2026-05-25 05:57:57.849422'),
    -- 정산 4건 (가맹점 홈 '최근 정산 내역' 표시 대상)
    (10007, '550e8400-e29b-41d4-a716-446655440007', 'EXCHANGE', 'SUCCESS',
      50000.00, 0.00, 0.00, 1002, NULL, 1002, NULL, NULL, NULL,
     0, '2026-05-26 08:57:57.849422', '2026-05-26 08:57:57.849422'),
    (10008, '8f81c0d1-58cf-11f1-a33d-00155d95479e', 'EXCHANGE', 'SUCCESS',
     120000.00, 0.00, 0.00, 1002, NULL, 1002, NULL, 1002, NULL,
     0, '2026-05-24 15:53:11.000000', '2026-05-24 15:53:11.000000'),
    (10009, '8f81f954-58cf-11f1-a33d-00155d95479e', 'EXCHANGE', 'SUCCESS',
      85000.00, 0.00, 0.00, 1002, NULL, 1002, NULL, 1002, NULL,
     0, '2026-05-25 15:53:11.000000', '2026-05-25 15:53:11.000000'),
    (10010, '8f82034a-58cf-11f1-a33d-00155d95479e', 'EXCHANGE', 'SUCCESS',
     200000.00, 0.00, 0.00, 1002, NULL, 1002, NULL, 1002, NULL,
     0, '2026-05-26 12:53:11.000000', '2026-05-26 12:53:11.000000')
ON DUPLICATE KEY UPDATE
    transaction_type        = VALUES(transaction_type),
    status                  = VALUES(status),
    amount                  = VALUES(amount),
    discount_amount         = VALUES(discount_amount),
    discount_rate           = VALUES(discount_rate),
    from_party_id           = VALUES(from_party_id),
    to_party_id             = VALUES(to_party_id),
    from_wallet_id          = VALUES(from_wallet_id),
    to_wallet_id            = VALUES(to_wallet_id),
    from_account_id         = VALUES(from_account_id),
    to_account_id           = VALUES(to_account_id),
    reconcile_attempt_count = VALUES(reconcile_attempt_count),
    updated_at              = VALUES(updated_at);

SELECT id, from_party_id, to_party_id, transaction_type, status, amount, DATE(created_at) AS date
FROM transaction ORDER BY id;


-- ============================================================
-- hangang_pay_bank
-- ============================================================
USE hangang_pay_bank;

-- institution: hangang_pay.institution 과 id 일치 필수
INSERT INTO institution (id, institution_code, institution_name, created_at, updated_at)
VALUES
    (1, 'BoK', '한국은행', NOW(), NOW()),
    (2, 'WR',  '우리은행', NOW(), NOW()),
    (3, 'SH',  '신한은행', NOW(), NOW()),
    (4, 'HN',  '하나은행', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    institution_code = VALUES(institution_code),
    institution_name = VALUES(institution_name),
    updated_at       = VALUES(updated_at);

SELECT id, institution_code, institution_name FROM institution ORDER BY id;

-- bank_account: account_number 에 UNIQUE 제약 없어 WHERE NOT EXISTS 사용
INSERT INTO bank_account (institution_id, account_number, owner_name, balance, created_at, updated_at)
SELECT 2, '200-000-100001', 'local-user', 1000000.0000, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM bank_account WHERE institution_id = 2 AND account_number = '200-000-100001');

INSERT INTO bank_account (institution_id, account_number, owner_name, balance, created_at, updated_at)
SELECT 2, '200-000-200001', 'Local Merchant', 1000000.0000, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM bank_account WHERE institution_id = 2 AND account_number = '200-000-200001');

INSERT INTO bank_account (institution_id, account_number, owner_name, balance, created_at, updated_at)
SELECT 2, '20012345678', '김한강', 1000000.0000, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM bank_account WHERE institution_id = 2 AND account_number = '20012345678');

INSERT INTO bank_account (institution_id, account_number, owner_name, balance, created_at, updated_at)
SELECT 2, '20098765432', '홍길동', 500000.0000, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM bank_account WHERE institution_id = 2 AND account_number = '20098765432');

-- bank-account-seed.sql 의 4개 계좌
INSERT INTO bank_account (institution_id, account_number, owner_name, balance, created_at, updated_at)
SELECT 2, '222000000001', '홍길동', 1000000.0000, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM bank_account WHERE institution_id = 2 AND account_number = '222000000001');

INSERT INTO bank_account (institution_id, account_number, owner_name, balance, created_at, updated_at)
SELECT 2, '222000000002', '김한강', 500000.0000, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM bank_account WHERE institution_id = 2 AND account_number = '222000000002');

INSERT INTO bank_account (institution_id, account_number, owner_name, balance, created_at, updated_at)
SELECT 3, '333000000001', '이성수', 750000.0000, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM bank_account WHERE institution_id = 3 AND account_number = '333000000001');

INSERT INTO bank_account (institution_id, account_number, owner_name, balance, created_at, updated_at)
SELECT 4, '444000000001', '박서울', 750000.0000, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM bank_account WHERE institution_id = 4 AND account_number = '444000000001');

SELECT id, institution_id, account_number, owner_name, balance
FROM bank_account ORDER BY institution_id, account_number;

-- bank_wallet: wallet_address 에 UNIQUE 제약 없어 WHERE NOT EXISTS 사용
-- encrypted_private_key 는 실제 키값이므로 더미값 사용
INSERT INTO bank_wallet (institution_id, wallet_address, balance, encrypted_private_key, created_at, updated_at)
SELECT 2, '0x1111111111111111111111111111111111111111', 1000000.0000, 'dummy-key-user', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM bank_wallet WHERE wallet_address = '0x1111111111111111111111111111111111111111');

INSERT INTO bank_wallet (institution_id, wallet_address, balance, encrypted_private_key, created_at, updated_at)
SELECT 2, '0x2222222222222222222222222222222222222222', 1000000.0000, 'dummy-key-merchant', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM bank_wallet WHERE wallet_address = '0x2222222222222222222222222222222222222222');

SELECT id, institution_id, wallet_address, balance FROM bank_wallet ORDER BY id;
