# ERD

현재 ERD 기준. 실제 구현 시 테이블/컬럼명은 영문 snake_case를 사용한다.

## Diagram

```dbml
Table party {
  id bigint [pk, increment, note: '사용자 통합 식별자']
  party_type varchar(20) [not null, note: '사용자 종류 (USER / MERCHANT)']
  created_at datetime [not null, note: '생성 시각']
  updated_at datetime [not null, note: '수정 시각']
}

Table users {
  id bigint [pk, increment, note: '소비자 자체 식별자']
  party_id bigint [not null, unique, ref: > party.id, note: 'party 슈퍼타입 참조 (1:1)']
  username varchar(50) [not null, note: '닉네임/표시명']
  password_hash varchar(255) [not null, note: '비밀번호 해시']
  payment_pin_hash varchar(255) [not null, note: '결제 PIN 해시']
  phone_number varchar(20) [not null, unique, note: '휴대폰 번호 (로그인 식별자)']
  birth_date date [note: '생년월일 (본인 인증용)']
  region varchar(50) [note: '거주 지역']
  created_at datetime [not null, note: '생성 시각']
  updated_at datetime [not null, note: '수정 시각']
}

Table merchant {
  id bigint [pk, increment, note: '가맹점 자체 식별자']
  party_id bigint [not null, unique, ref: > party.id, note: 'party 슈퍼타입 참조 (1:1)']
  username varchar(50) [not null, note: '가맹점 닉네임']
  password_hash varchar(255) [not null, note: '비밀번호 해시']
  business_number varchar(30) [not null, unique, note: '사업자번호 (로그인 식별자)']
  merchant_name varchar(100) [not null, note: '가맹점명']
  owner_name varchar(50) [not null, note: '대표자명']
  phone_number varchar(20) [note: '영업장 연락처']
  address varchar(255) [note: '영업장 주소']
  latitude decimal(10,7) [note: '위도 (지도 표시용)']
  longitude decimal(10,7) [note: '경도 (지도 표시용)']
  created_at datetime [not null, note: '생성 시각']
  updated_at datetime [not null, note: '수정 시각']
}

Table institution {
  id bigint [pk, note: '기관 식별자 (bank의 institution.id와 동일, 시드 데이터)']
  institution_code varchar(20) [not null, unique, note: '기관 코드']
  institution_name varchar(100) [not null, note: '기관명 (예: 우리은행, 한국은행)']
  created_at datetime [not null, note: '생성 시각']
  updated_at datetime [not null, note: '수정 시각']
}

Table account {
  id bigint [pk, increment, note: '식별자']
  party_id bigint [not null, ref: > party.id, note: '소유자 (party 참조)']
  institution_id bigint [not null, ref: > institution.id, note: '소속 기관 (BE 캐시 참조)']
  account_type varchar(20) [not null, note: '계좌 종류 (PRIMARY / SECONDARY / SETTLEMENT)']
  account_number varchar(50) [not null, note: '계좌번호 (bank_account와 매칭 키)']
  created_at datetime [not null, note: '생성 시각']
  updated_at datetime [not null, note: '수정 시각']
}

Table wallet {
  id bigint [pk, increment, note: '식별자']
  party_id bigint [not null, unique, ref: > party.id, note: '소유자 (1인 1지갑)']
  institution_id bigint [not null, ref: > institution.id, note: '소속 기관 (BE 캐시 참조)']
  address varchar(100) [not null, unique, note: '블록체인 지갑 주소 (bank_wallet과 매칭 키)']
  created_at datetime [not null, note: '생성 시각']
  updated_at datetime [not null, note: '수정 시각']
}

Table transaction {
  id bigint [pk, increment, note: '사건 식별자 (물리적 PK)']
  transaction_uuid varchar(36) [not null, unique, note: '비즈니스 거래 식별자 (프론트 생성 멱등키)']
  original_transaction_uuid varchar(36) [note: 'CANCEL 전용 - 원본 PAYMENT의 transaction_uuid 참조']
  transaction_type varchar(20) [not null, note: '거래 종류 (CHARGE / EXCHANGE / PAYMENT / CANCEL)']

  from_party_id bigint [ref: > party.id, note: '출발 사용자']
  to_party_id bigint [ref: > party.id, note: '도착 사용자']
  from_account_id bigint [ref: > account.id, note: 'CHARGE 전용 - 출금 계좌']
  to_account_id bigint [ref: > account.id, note: 'EXCHANGE 전용 - 입금 계좌']
  from_wallet_id bigint [ref: > wallet.id, note: 'EXCHANGE/PAYMENT/CANCEL - 출금 지갑']
  to_wallet_id bigint [ref: > wallet.id, note: 'CHARGE/PAYMENT/CANCEL - 입금 지갑']

  amount decimal(18,2) [not null, note: '거래 금액']
  discount_amount decimal(18,2) [default: 0, note: '할인 금액 (CHARGE/EXCHANGE 전용)']
  discount_rate decimal(5,2) [default: 0, note: '할인율 (CHARGE/EXCHANGE 전용)']

  approval_number varchar(50) [unique, note: 'PAYMENT 전용 - 승인번호']
  item_name varchar(100) [note: 'PAYMENT 전용 - 상품명']

  tx_hash varchar(100) [note: '블록체인 증거 (blockchain_ledger 매칭, FK 없음)']
  bank_transaction_id varchar(100) [note: '은행 거래 ID (account_ledger 매칭, FK 없음)']

  status varchar(20) [not null, note: '거래 상태 (PENDING / SUCCESS / FAILED)']
  created_at datetime [not null, note: '생성 시각']
  updated_at datetime [not null, note: '수정 시각']
}
```

## Modeling Rules

- `party`는 `users`와 `merchant`의 상위 엔티티다.
- `users.party_id`와 `merchant.party_id`는 각각 `party.id`와 1:1로 연결된다.
- `account`와 `wallet`은 `party_id`를 통해 소비자와 가맹점 모두 소유할 수 있다.
- `wallet.party_id`는 unique이며 한 party는 하나의 서비스 지갑을 가진다.
- `institution`은 BE에서 계좌/지갑이 참조하는 기관 캐시만 담당한다.
- `institution.id`는 bank 모듈의 `institution.id`와 동일한 시드 데이터 식별자를 사용한다.
- `bank_account`, `bank_wallet`, 컨트랙트 주소와 배포 책임은 `hangang-pay-bank`에 둔다.
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
| `CANCEL` | `from_party_id`, `to_party_id`, `from_wallet_id`, `to_wallet_id` | 결제 취소로 원 결제의 반대 방향 이체 |

## Settlement Meaning

정산은 가맹점이 보유한 토큰을 1:1 비율로 계좌 환전 신청한 기록을 의미한다.

`정산 내역`이라는 표현은 별도 정산 테이블이 아니라 `transaction` 테이블의 가맹점 환전 거래 조회 화면/API에서 사용한다. 조회 대상은 `from_party_id`가 현재 가맹점의 `partyId`이고 `transaction_type`이 `EXCHANGE`인 거래다.
