# Bank (BaaS): hangang-pay-bank

BaaS 서버. 한강페이 BE의 BankClient가 호출하는 은행 + 블록체인 도메인.

## Responsibility

- 은행 원장: `institution`, `bank_account`, `bank_wallet`, `account_ledger`
- 블록체인 통합: 컨트랙트 배포(`contract`), Web3j 호출(`ContractCallService`), 거래 로그(`blockchain_ledger`)
- Custodial 지갑: keypair 생성/암호화 보관, BE는 `walletAddress`만 받아 보관

## Tech Stack

- Java 17, Spring Boot 3.5.14, Gradle
- Spring Data JPA + MySQL
- SpringDoc OpenAPI → `/swagger-ui/index.html`
- Web3j 4.12.3 → Besu 노드 통신
- Lombok, Spotless (google-java-format AOSP)
- Port: **8081** (BE는 8080)

## Package Structure

```
family.fisa.hangangpaybank
├── domain
│   ├── institution           # 기관, 은행 계좌/지갑, 컨트랙트
│   ├── blockchain            # blockchain_ledger + ContractCallService
│   ├── ledger                # account_ledger (입출금 원장)
│   └── transaction           # 거래 처리 (charge/exchange/payment/cancel)
└── global
    ├── code/{error,success}  # BaseErrorCode/BaseSuccessCode + General*
    ├── exception             # BusinessException, GlobalExceptionHandler
    ├── response              # ApiResponse 공통 래퍼
    ├── entity                # BaseEntity (createdAt, updatedAt)
    ├── logging               # MDC 요청 로그 컨텍스트 (requestId, X-Request-Id 수신)
    └── config                # JpaConfig (auditing)
```

도메인 내부 기본 구조: `entity/ repository/ service/ dto/{request,response}/ controller/ code/{error}/`

## API Convention

- prefix `/api/v1`
- 응답 공통: `{ isSuccess, status, code, message, result }`
- 엔티티 직접 반환 금지. DTO 변환 필수.
- Service 분리: `XxxQueryService`(readOnly) / `XxxCommandService` — 단일 `XxxService` 금지

## Error/Success Code

- enum 이름 = `code` 문자열. ex: `INSTITUTION_NOT_FOUND` → `"INSTITUTION_NOT_FOUND"`
- 도메인 접두어로 그룹핑 (`INSTITUTION_`, `BANK_ACCOUNT_`, `TRANSACTION_`, `BLOCKCHAIN_`, `COMMON_`)
- opaque code (`INSTITUTION404`, `COMMON200`) 신규 작성 금지

## Repository Pattern

- 단순 도메인: `XxxRepository extends JpaRepository<Xxx, Long>` (institution, bank_account, bank_wallet, contract)
- 어댑터 분리가 필요할 때만: 도메인 루트 인터페이스(`XxxRepository`) + `XxxRepositoryImpl` 어댑터 + `jpa/XxxJpaRepository` (현재 `blockchain_ledger`, `account_ledger`)

## Custodial Wallet

- bank가 EC keypair 생성 (`Keys.createEcKeyPair()`)
- private key는 AES/GCM 암호화 후 `bank_wallet.encrypted_private_key`에 저장 (`WalletKeyCipher`)
- BE에는 `walletAddress`만 응답. private key 외부 노출 금지.
- 환경변수: `WALLET_KEY_CIPHER_SECRET` (application.yaml의 `wallet.key-cipher.secret`)

## Blockchain Contracts

- 4 종류: `CBDC`, `DEPOSIT_TOKEN`, `SETTLEMENT`, `LOCAL_CURRENCY` (`ContractType` enum)
- 컨트랙트 주소 저장: `contract` 테이블
- artifact: `src/main/resources/contracts/{CBDCToken,DepositToken,LocalCurrencyPolicy,Settlement}.json`
- 호출: `ContractCallService` (pay/cancelPayment/charge/refund) — LOCAL_CURRENCY 컨트랙트 owner credentials로 서명

## Commands

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
./gradlew test
./gradlew build -x test
```

`hangang-pay-bank/`에서 실행. `./gradlew` 사용 (시스템 gradle 금지).

## Configuration

- `application.yaml`: 공통 설정 (포트 8081, springdoc, wallet/blockchain placeholder). 커밋 대상.
- `application-local.yml`: DB 연결 정보 등 secrets. **커밋 금지** (.gitignore).
- `application-dev.yml`, `application-prod.yml`: 환경별 ddl-auto 등 override.

## ERD

`hangang-pay-bank/docs/bank-erd.md` — 테이블/컬럼 설계의 단일 진실 공급원.

엔티티 추가·변경 시 ERD를 먼저 확인하고, 컬럼 구성을 ERD에 맞춘다. 설계 변경이 필요하면 엔티티와 ERD를 함께 수정한다.

## Async Payment Phase Work

`dev-docs/bank-blockchain-async-payment-plan.md`에 Phase 0–10 구현 계획이 있다.

**각 Phase 완료 후 반드시:**

1. 해당 Phase의 Verification Command로 테스트 통과 확인
2. 계획 문서에 `#### 구현 결과` 섹션 추가 (변경 내용 요약, 테스트 결과)
3. Phase Checklist의 해당 항목을 `[x]`로 체크
4. 구현 파일과 계획 문서를 함께 커밋

## What NOT to Do

- 컨트롤러에 비즈니스 로직 작성 금지
- `@Autowired` 필드 주입 금지 — 생성자 주입 + `@RequiredArgsConstructor`
- 엔티티를 응답으로 직접 반환 금지
- `application-local.yml` 커밋 금지
- private key 평문 로깅 금지
- BE의 도메인 객체(`User`, `Merchant`, `Party` 등) bank에 끌어오기 금지 — BE 책임
