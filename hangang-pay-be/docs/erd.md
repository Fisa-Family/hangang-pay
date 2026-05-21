# ERD

현재 ERD 기준. 실제 구현 시 테이블/컬럼명은 영문 snake_case를 사용하고, ERD 초안의 한글명·공백명·슬래시명·오타 컬럼은 의미에 맞게 정규화한다.

## Diagram

```mermaid
erDiagram
  PARTY {
    BIGINT id PK "식별자"
    VARCHAR party_type "USER | MERCHANT"
    DATETIME created_at
    DATETIME updated_at
  }

  USER {
    BIGINT user_id PK
    BIGINT party_id FK "party.id"
    VARCHAR username
    VARCHAR password_hash
    VARCHAR payment_pin_hash
    VARCHAR phone_number
    DATE birth_date
    VARCHAR region
  }

  MERCHANT {
    BIGINT merchant_id PK
    BIGINT party_id FK "party.id"
    VARCHAR username
    VARCHAR password_hash
    VARCHAR business_number
    VARCHAR merchant_name
    VARCHAR owner_name
    VARCHAR phone_number
    VARCHAR address
    DECIMAL latitude
    DECIMAL longitude
  }

  INSTITUTION {
    BIGINT id PK
    VARCHAR institution_code
    VARCHAR institution_name
    VARCHAR account_number
    VARCHAR wallet_address
    TEXT encrypted_private_key
    VARCHAR enode_url
    VARCHAR rpc_endpoint
    DATETIME created_at
    DATETIME updated_at
  }

  ACCOUNT {
    BIGINT id PK
    BIGINT party_id FK "party.id"
    BIGINT institution_id FK "institution.id"
    VARCHAR account_type "PRIMARY | SECONDARY | SETTLEMENT"
    VARCHAR account_number
    DATETIME created_at
    DATETIME updated_at
  }

  WALLET {
    BIGINT id PK
    BIGINT party_id FK "party.id"
    BIGINT institution_id FK "institution.id"
    VARCHAR address
    DATETIME created_at
    DATETIME updated_at
  }

  BANK_ACCOUNT {
    BIGINT id PK
    BIGINT institution_id FK "institution.id"
    VARCHAR account_number
    DECIMAL balance
    DATETIME created_at
    DATETIME updated_at
  }

  BANK_WALLET {
    BIGINT id PK
    BIGINT institution_id FK "institution.id"
    VARCHAR wallet_address
    DECIMAL balance
    TEXT encrypted_private_key
    DATETIME created_at
    DATETIME updated_at
  }

  CONTRACT_ADDRESS {
    BIGINT id PK
    BIGINT institution_id FK "institution.id"
    VARCHAR name "CBDC | DEPOSIT_TOKEN | CONTRACT"
    CHAR address
    TIMESTAMP created_at
    TIMESTAMP updated_at
  }

  FUND_TRANSFER {
    BIGINT id PK
    BIGINT party_id FK "party.id"
    BIGINT account_id FK "account.id"
    BIGINT wallet_id FK "wallet.id"
    DECIMAL amount
    DECIMAL discount_amount
    DECIMAL discount_rate
    VARCHAR status "PENDING | SUCCESS | FAILED"
    VARCHAR transfer_type "CHARGE | EXCHANGE"
    DATETIME created_at
    DATETIME updated_at
  }

  PAYMENT {
    BIGINT id PK
    BIGINT payer_party_id FK "party.id"
    BIGINT payee_party_id FK "party.id"
    BIGINT payer_wallet_id FK "wallet.id"
    BIGINT payee_wallet_id FK "wallet.id"
    VARCHAR item_name
    DECIMAL amount
    VARCHAR approval_number
    VARCHAR status "PENDING | SUCCESS | FAILED"
    DATETIME created_at
    DATETIME updated_at
  }

  PAYMENT_CANCELLATION {
    BIGINT id PK
    BIGINT payment_id FK "payment.id"
    BIGINT party_id FK "party.id"
    DECIMAL amount
    VARCHAR cancel_approval_number
    VARCHAR status "PENDING | SUCCESS | FAILED"
    DATETIME created_at
    DATETIME updated_at
  }

  BLOCKCHAIN_TX {
    BIGINT id PK
    BIGINT reference_id "참조 엔티티 id"
    VARCHAR reference_type "FUND_TRANSFER | PAYMENT | PAYMENT_CANCELLATION"
    VARCHAR tx_hash
    VARCHAR status "PENDING | CONFIRMED | FAILED"
    DATETIME created_at
    DATETIME updated_at
  }

  PARTY ||--|| USER : "has user profile"
  PARTY ||--|| MERCHANT : "has merchant profile"
  PARTY ||--o{ ACCOUNT : "owns"
  PARTY ||--o{ WALLET : "owns"
  PARTY ||--o{ FUND_TRANSFER : "requests"
  PARTY ||--o{ PAYMENT : "pays"
  PARTY ||--o{ PAYMENT : "receives"
  PARTY ||--o{ PAYMENT_CANCELLATION : "requests"

  INSTITUTION ||--o{ ACCOUNT : "issues linked account"
  INSTITUTION ||--o{ WALLET : "issues linked wallet"
  INSTITUTION ||--o{ BANK_ACCOUNT : "holds"
  INSTITUTION ||--o{ BANK_WALLET : "holds"
  INSTITUTION ||--o{ CONTRACT_ADDRESS : "deploys"

  ACCOUNT ||--o{ FUND_TRANSFER : "used by"
  WALLET ||--o{ FUND_TRANSFER : "used by"
  WALLET ||--o{ PAYMENT : "payer wallet"
  WALLET ||--o{ PAYMENT : "payee wallet"
  PAYMENT ||--o{ PAYMENT_CANCELLATION : "cancelled by"
```

## Modeling Rules

- `party`는 `user`와 `merchant`의 상위 엔티티다.
- `account`와 `wallet`은 `party_id`를 통해 소비자와 가맹점 모두 소유할 수 있다.
- `bank_account`, `bank_wallet`, `contract_address`는 `institution` 도메인에 둔다.
- `blockchain_tx.reference_type`은 `FUND_TRANSFER`, `PAYMENT`, `PAYMENT_CANCELLATION`만 사용한다.
- `blockchain_tx.reference_id`는 `reference_type`에 따라 원본 테이블의 id를 의미한다.
- `blockchain_tx.reference_id`는 다형 참조이므로 단일 FK로 특정 테이블 하나에만 묶지 않는다.
- 사용자별/가맹점별 블록체인 기록 조회는 원본 테이블과 조인해서 `party_id`를 찾는다.
- 1원 인증용 트랜잭션 테이블은 추후 별도 추가한다.

## Enum Values

| Field | Values                                             |
| --- |----------------------------------------------------|
| `party.party_type` | `USER`, `MERCHANT`                                 |
| `account.account_type` | `PRIMARY`, `SECONDARY`, `SETTLEMENT`                |
| `fund_transfer.transfer_type` | `CHARGE`, `EXCHANGE`                               |
| `fund_transfer.status` | `PENDING`, `SUCCESS`, `FAILED`                     |
| `payment.status` | `PENDING`, `SUCCESS`, `FAILED`                     |
| `payment_cancellation.status` | `PENDING`, `SUCCESS`, `FAILED`                     |
| `blockchain_tx.reference_type` | `FUND_TRANSFER`, `PAYMENT`, `PAYMENT_CANCELLATION` |
| `blockchain_tx.status` | `PENDING`, `CONFIRMED`, `FAILED`                   |
| `contract_address.name` | `CBDC`, `DEPOSIT_TOKEN`, `CONTRACT`                |

## Settlement Meaning

정산은 별도 적재·배치·입금 프로세스가 아니다. 소비자가 가맹점에게 결제하면 가맹점 월렛으로 코인이 즉시 이체된다. 가맹점은 쌓인 코인을 1:1 비율로 계좌 환전할 수 있다.

`정산 내역`이라는 표현은 실제 정산 테이블이 아니라 `payment`와 `payment_cancellation` 기반 기록 조회 화면/API에서만 사용한다.
