# ERD

현재 JPA 엔티티 기준. 테이블/컬럼명은 영문 snake_case를 사용한다.

## Diagram

```dbml
Table party {
  id bigint [pk, increment, note: '사용자 통합 식별자']
  party_type varchar(255) [not null, note: 'USER / MERCHANT']
  created_at datetime [not null]
  updated_at datetime [not null]
}

Table users {
  id bigint [pk, increment, note: '소비자 자체 식별자']
  party_id bigint [not null, unique, ref: > party.id]
  username varchar(255) [not null, note: '사용자 실명']
  password_hash varchar(255) [not null]
  payment_pin_hash varchar(255) [not null]
  phone_number varchar(20) [not null, unique]
  birth_date date
  region varchar(255)
  created_at datetime [not null]
  updated_at datetime [not null]
}

Table merchant {
  id bigint [pk, increment, note: '가맹점 자체 식별자']
  party_id bigint [not null, unique, ref: > party.id]
  username varchar(255) [not null, unique, note: '가맹점 사용자명']
  password_hash varchar(255) [not null]
  business_number varchar(255) [not null, unique]
  merchant_name varchar(255) [not null]
  owner_name varchar(255) [not null]
  phone_number varchar(255)
  address varchar(255)
  latitude decimal(10,7)
  longitude decimal(10,7)
  created_at datetime [not null]
  updated_at datetime [not null]
}

Table institution {
  id bigint [pk, note: '기관 식별자. bank의 institution.id와 같은 시드 데이터 사용']
  institution_code varchar(20) [not null, unique]
  institution_name varchar(100) [not null]
  created_at datetime [not null]
  updated_at datetime [not null]
}

Table account {
  id bigint [pk, increment]
  party_id bigint [not null, ref: > party.id]
  institution_id bigint [not null, ref: > institution.id]
  account_type varchar(255) [not null, note: 'PRIMARY / SECONDARY / SETTLEMENT']
  account_number varchar(255) [not null]
  created_at datetime [not null]
  updated_at datetime [not null]
}

Table wallet {
  id bigint [pk, increment]
  party_id bigint [not null, unique, ref: > party.id]
  institution_id bigint [not null, ref: > institution.id]
  address varchar(100) [not null, unique, note: 'bank_wallet과 매칭되는 지갑 주소']
  created_at datetime [not null]
  updated_at datetime [not null]
}

Table transaction {
  id bigint [pk, increment, note: '물리적 PK']
  transaction_uuid varchar(36) [not null, unique, note: '비즈니스 거래 식별자']
  original_transaction_uuid varchar(36) [note: 'CANCEL 전용 원본 PAYMENT transaction_uuid']
  transaction_type varchar(20) [not null, note: 'CHARGE / EXCHANGE / PAYMENT / CANCEL']
  status varchar(20) [not null, note: 'PENDING / SUCCESS / FAILED']

  from_party_id bigint [ref: > party.id]
  to_party_id bigint [ref: > party.id]
  from_account_id bigint [ref: > account.id]
  to_account_id bigint [ref: > account.id]
  from_wallet_id bigint [ref: > wallet.id]
  to_wallet_id bigint [ref: > wallet.id]

  amount decimal(18,2) [not null]
  discount_amount decimal(18,2)
  discount_rate decimal(5,2)

  approval_number varchar(50) [unique, note: 'PAYMENT/CANCEL 승인번호']
  item_name varchar(100) [note: 'PAYMENT 상품명']

  tx_hash varchar(100) [note: 'blockchain_ledger 매칭 키. FK 없음']
  bank_transaction_id varchar(100) [note: 'account_ledger.id 값. FK 없음']

  created_at datetime [not null]
  updated_at datetime [not null]
}
```

## Modeling Rules

- `party`는 `users`와 `merchant`의 상위 엔티티다.
- `users.party_id`와 `merchant.party_id`는 각각 `party.id`와 1:1로 연결된다.
- `account`와 `wallet`은 `party_id`를 통해 소비자와 가맹점 모두 소유할 수 있다.
- `wallet.party_id`는 unique이며 한 party는 하나의 서비스 지갑을 가진다.
- `institution`은 BE에서 계좌/지갑이 참조하는 기관 캐시만 담당한다.
- `institution.id`는 bank 모듈의 `institution.id`와 동일한 시드 데이터 식별자를 사용한다.
- `bank_account`, `bank_wallet`, `blockchain_ledger`, 컨트랙트 주소와 실행 책임은 `hangang-pay-bank`에 둔다.
- BE는 bank가 보유한 custodial wallet의 address만 `wallet.address`에 저장한다.
- 충전, 환전, 결제, 결제 취소는 `transaction` 테이블에서 `transaction_type`으로 구분한다.
- 블록체인 증거와 은행 거래 식별자는 `transaction.tx_hash`, `transaction.bank_transaction_id`에 저장하며 FK로 묶지 않는다.

## Enum Values

| Field | Values |
| --- | --- |
| `party.party_type` | `USER`, `MERCHANT` |
| `account.account_type` | `PRIMARY`, `SECONDARY`, `SETTLEMENT` |
| `transaction.transaction_type` | `CHARGE`, `EXCHANGE`, `PAYMENT`, `CANCEL` |
| `transaction.status` | `PENDING`, `SUCCESS`, `FAILED` |

## Transaction Rules

| Type | Required relations | Meaning |
| --- | --- | --- |
| `CHARGE` | `from_party_id`, `from_account_id`, `to_wallet_id` | 계좌 출금 후 토큰 충전 |
| `EXCHANGE` | `from_party_id`, `to_account_id`, `from_wallet_id` | 토큰 환전 후 계좌 입금 |
| `PAYMENT` | `from_party_id`, `to_party_id`, `from_wallet_id`, `to_wallet_id` | 사용자 지갑에서 가맹점 지갑으로 결제 |
| `CANCEL` | `from_party_id`, `to_party_id`, `from_wallet_id`, `to_wallet_id`, `original_transaction_uuid` | 결제 취소로 원 결제의 반대 방향 이체 |

## Settlement Meaning

정산은 가맹점이 보유한 토큰을 계좌로 환전 신청한 기록을 의미한다.

`정산 내역`이라는 표현은 별도 정산 테이블이 아니라 `transaction` 테이블의 가맹점 환전 거래 조회 화면/API에서 사용한다. 조회 대상은 `from_party_id`가 현재 가맹점의 `partyId`이고 `transaction_type`이 `EXCHANGE`인 거래다.
