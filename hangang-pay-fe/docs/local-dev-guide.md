# 가맹점 로컬 데이터 확인 가이드

이 문서는 로컬 실행 후 가맹점 홈에서 백엔드 더미 정산 데이터를 확인하는 기준을 정리한다.

## 실행 순서

1. 백엔드를 `local` 프로필로 실행한다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

2. 프론트엔드를 실행한다.

```bash
npm run dev
```

3. 가맹점 홈 화면에서 최근 정산 내역을 확인한다.

## 현재 로컬 DB 기준 데이터

현재 `hangang_pay` DB 기준 주요 데이터는 다음과 같다.

| 구분 | 값 |
| --- | --- |
| 사용자 party | `party.id = 1001`, `party_type = USER` |
| 가맹점 party | `party.id = 1002`, `party_type = MERCHANT` |
| 가맹점 | `merchant.id = 1001`, `party_id = 1002`, `phone_number = 01087654321`, `merchant_name = 한강상점` |
| 사용자 지갑 | `wallet.id = 1001`, `party_id = 1001`, `institution_id = 2`, `address = 0x1111111111111111111111111111111111111111` |
| 가맹점 지갑 | `wallet.id = 1002`, `party_id = 1002`, `institution_id = 2`, `address = 0x2222222222222222222222222222222222222222` |

가맹점 홈의 최근 정산 내역은 다음 조건의 `transaction` 데이터를 사용한다.

```sql
SELECT id, from_party_id, transaction_type, status, amount, created_at
FROM transaction
WHERE from_party_id = 1002
  AND transaction_type = 'EXCHANGE'
  AND status = 'SUCCESS'
ORDER BY created_at DESC, id DESC;
```

현재 조회 결과는 다음 1건이다.

| id | from_party_id | transaction_type | status | amount | created_at |
| --- | --- | --- | --- | --- | --- |
| `10007` | `1002` | `EXCHANGE` | `SUCCESS` | `50000.00` | `2026-05-26 08:57:57.849422` |

따라서 가맹점 세션의 `partyId`가 `1002`이면 `/api/v1/merchant/settlements?size=4`는 이 정산 내역을 반환해야 한다.

## 데이터 저장 SQL

아래 SQL은 위 조회 결과를 로컬 DB에 재현하는 용도다. FK가 있으므로 `institution -> party -> merchant/wallet -> transaction` 순서로 실행한다.

```sql
INSERT INTO institution (
    id,
    institution_code,
    institution_name,
    created_at,
    updated_at
)
VALUES (
    2,
    'WR',
    '우리은행',
    NOW(6),
    NOW(6)
)
ON DUPLICATE KEY UPDATE
    institution_code = VALUES(institution_code),
    institution_name = VALUES(institution_name),
    updated_at = VALUES(updated_at);

INSERT INTO party (
    id,
    party_type,
    created_at,
    updated_at
)
VALUES
    (1001, 'USER', NOW(6), NOW(6)),
    (1002, 'MERCHANT', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE
    party_type = VALUES(party_type),
    updated_at = VALUES(updated_at);

INSERT INTO merchant (
    id,
    party_id,
    username,
    password_hash,
    business_number,
    merchant_name,
    owner_name,
    phone_number,
    address,
    latitude,
    longitude,
    created_at,
    updated_at
)
VALUES (
    1001,
    1002,
    'local-merchant',
    '$2a$10$YKiYZew/jOJ6FbKbO6qUE.2MlGP5uqBrvoTYgjHsssZ9SsToB/dc.',
    '123-45-67890',
    '한강상점',
    '로컬점주',
    '01087654321',
    '서울시 성동구',
    NULL,
    NULL,
    NOW(6),
    NOW(6)
)
ON DUPLICATE KEY UPDATE
    party_id = VALUES(party_id),
    username = VALUES(username),
    password_hash = VALUES(password_hash),
    business_number = VALUES(business_number),
    merchant_name = VALUES(merchant_name),
    owner_name = VALUES(owner_name),
    phone_number = VALUES(phone_number),
    address = VALUES(address),
    latitude = VALUES(latitude),
    longitude = VALUES(longitude),
    updated_at = VALUES(updated_at);

INSERT INTO wallet (
    id,
    party_id,
    institution_id,
    address,
    created_at,
    updated_at
)
VALUES
    (
        1001,
        1001,
        2,
        '0x1111111111111111111111111111111111111111',
        NOW(6),
        NOW(6)
    ),
    (
        1002,
        1002,
        2,
        '0x2222222222222222222222222222222222222222',
        NOW(6),
        NOW(6)
    )
ON DUPLICATE KEY UPDATE
    party_id = VALUES(party_id),
    institution_id = VALUES(institution_id),
    address = VALUES(address),
    updated_at = VALUES(updated_at);

INSERT INTO transaction (
    id,
    transaction_uuid,
    transaction_type,
    status,
    amount,
    discount_amount,
    discount_rate,
    from_party_id,
    from_wallet_id,
    to_party_id,
    to_wallet_id,
    from_account_id,
    to_account_id,
    original_transaction_uuid,
    approval_number,
    bank_transaction_id,
    item_name,
    tx_hash,
    reconcile_attempt_count,
    created_at,
    updated_at
)
VALUES (
    10007,
    '550e8400-e29b-41d4-a716-446655440007',
    'EXCHANGE',
    'SUCCESS',
    50000.00,
    0.00,
    0.00,
    1002,
    1002,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    0,
    '2026-05-26 08:57:57.849422',
    '2026-05-26 08:57:57.849422'
)
ON DUPLICATE KEY UPDATE
    transaction_type = VALUES(transaction_type),
    status = VALUES(status),
    amount = VALUES(amount),
    discount_amount = VALUES(discount_amount),
    discount_rate = VALUES(discount_rate),
    from_party_id = VALUES(from_party_id),
    from_wallet_id = VALUES(from_wallet_id),
    to_party_id = VALUES(to_party_id),
    to_wallet_id = VALUES(to_wallet_id),
    from_account_id = VALUES(from_account_id),
    to_account_id = VALUES(to_account_id),
    original_transaction_uuid = VALUES(original_transaction_uuid),
    approval_number = VALUES(approval_number),
    bank_transaction_id = VALUES(bank_transaction_id),
    item_name = VALUES(item_name),
    tx_hash = VALUES(tx_hash),
    reconcile_attempt_count = VALUES(reconcile_attempt_count),
    created_at = VALUES(created_at),
    updated_at = VALUES(updated_at);
```

`merchant.password_hash`는 현재 로컬 DB에 들어있는 값을 그대로 문서화한 것이다. `/api/v1/auth/merchants/login`에서 `401 Unauthorized`가 나면 이 해시가 프론트에서 보내는 비밀번호와 맞지 않는 상태이므로, 원하는 비밀번호의 BCrypt 해시로 교체해야 한다.

## 주의할 점

`bank_wallet`에 `partyId = 10001`로 지갑을 생성한 것은 이 가맹점 정산 조회와 직접 연결되지 않는다. `/api/v1/merchant/settlements`는 은행 서버의 `bank_wallet`이 아니라 백엔드 DB의 로그인 세션 `partyId`와 `transaction.from_party_id`를 기준으로 조회한다.

`403 Forbidden`이 발생하면 더미 데이터 부족보다 먼저 가맹점 로그인 세션을 확인해야 한다. 가맹점 로그인 세션이 없거나 세션 role이 `MERCHANT`가 아니면 정산 API는 막힌다.

`401 Unauthorized`가 `/api/v1/auth/merchants/login`에서 발생하면 전화번호 또는 비밀번호가 DB의 가맹점 계정과 맞지 않는 상태다. 이 경우 정산 API를 호출해도 세션이 없어서 `403`이 이어진다.

오늘 매출과 오늘 결제 건수는 현재 화면에서 고정값으로 표시된다. 현재 로컬 데이터 확인 대상은 최근 정산 내역이다.

## DB 접속

로컬 MySQL은 다음 값으로 접속한다.

```bash
mysql -u root -p1234 hangang_pay
```
