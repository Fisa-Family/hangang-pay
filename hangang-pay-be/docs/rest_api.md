# REST API

모든 API 경로 앞에는 `/api/v1` prefix를 붙인다. 아래 표의 path는 prefix를 제외한 경로다.  
단, `MERCHANT-009`는 예외로 `/api/v2`를 사용한다.

## Common Rules

- 인증 방식은 세션 기반이다. JWT로 변경하지 않는다.
- `Auth` 값이 `O`이면 로그인 세션이 필요하다.
- `Auth` 값이 `X`이면 비로그인 호출이 가능하다.
- 모든 응답은 공통 래퍼를 사용한다.
- 모든 엔드포인트에는 SpringDoc 어노테이션을 작성한다.
- request/response JSON은 아직 확정하지 않는다. DTO 설계 시 사람이 직접 검토한다.
- 결제 승인번호 형식은 `APV-YYYY-NNNNNNNN`이다.
- 승인번호는 `payment.id`를 8자리 zero padding해서 생성한다. 예: `payment.id=25` -> `APV-2026-00000025`
- 승인번호 생성은 `payment` 저장으로 id를 확보한 뒤 수행한다.

```json
{
  "isSuccess": true,
  "status": 200,
  "code": "PAYMENT_EXECUTED",
  "message": "결제가 완료되었습니다.",
  "result": {}
}
```

## Role Rules

| Role | Meaning |
| --- | --- |
| `PUBLIC` | 비로그인 가능 |
| `USER` | 소비자 세션 필요 |
| `MERCHANT` | 가맹점 세션 필요 |
| `USER \| MERCHANT` | 소비자와 가맹점 모두 가능 |

계좌와 지갑 잔액 API는 소비자/가맹점 모두 사용한다. 서비스가 분리되어 있더라도 현재 세션의 `partyId`를 기준으로 자신의 데이터만 조회·변경한다.

## Payment Flow

QR에는 가맹점 id가 들어있다. 소비자가 QR을 스캔하면 가맹점 정보를 조회하고, 금액 입력 화면으로 전환한 뒤 결제를 실행한다.

```mermaid
sequenceDiagram
  participant U as User App
  participant API as Backend API
  participant BC as Blockchain

  U->>U: QR scan
  U->>API: GET /api/v1/merchants/{merchantPartyId}
  API-->>U: merchant payment target
  U->>API: POST /api/v1/payment
  API->>BC: transfer
  API-->>U: payment result
```

결제 취소는 시간 제한 없이 가능하다. 요청 주체는 가맹점이다.

## Settlement and Exchange

정산은 가맹점이 보유한 토큰을 1:1 비율로 계좌 환전 신청한 기록을 의미한다.

`/merchant/settlements`는 별도 정산 테이블 조회가 아니라 `transaction` 테이블에서 현재 가맹점의 환전 거래를 조회하는 API다. 조회 대상은 `from_party_id`가 현재 가맹점의 `partyId`이고 `transaction_type`이 `EXCHANGE`인 거래다.

서비스 용어는 `exchange`와 `환전`을 사용한다.

## Hold Policy

| API ID | Status | Reason |
| --- | --- | --- |
| `AUTH-001` | 구현 예정 | SMS 인증번호 발송. Octomo 사용 |
| `AUTH-002` | 구현 예정 | SMS 인증번호 검증. Octomo 사용 |
| `AUTH-003` | 구현 예정 | 계좌 1원 인증 발송. 인증 트랜잭션 테이블은 추후 추가 |
| `AUTH-004` | 구현 예정 | 계좌 1원 인증 검증. 인증 트랜잭션 테이블은 추후 추가 |
| `AUTH-005` | 구현 예정 | 비밀번호 재설정 |
| `WALLET-002` | 장기 보류 | 근처 가맹점 조회. 구현 복잡도 |

SMS 인증은 Octomo를 사용한다. SMS 발송 API도 백엔드에 둔다.

## API Catalog

| ID | Name | Method | Path | Auth | Role | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| `LOGIN-001` | 로그인 (소비자) | `POST` | `/auth/login/users` | `X` | `PUBLIC` | 세션 생성 |
| `LOGIN-002` | 로그인 (가맹점) | `POST` | `/auth/login/merchant` | `X` | `PUBLIC` | 세션 생성 |
| `LOGOUT-001` | 로그아웃 | `POST` | `/auth/logout` | `O` | `USER \| MERCHANT` | 현재 세션 삭제 |
| `REG-001` | 소비자 회원가입 | `POST` | `/auth/users/register` | `X` | `PUBLIC` | 휴대폰·계좌 인증 세션 확인 후 회원/주계좌/지갑 생성 |
| `REG-002` | 사업자 정보 조회 | `GET` | `/merchant/business-info` | `X` | `PUBLIC` | 쿼리 파라미터: `businessNumber` |
| `REG-003` | 가맹점 회원가입 | `POST` | `/auth/merchants/register` | `X` | `PUBLIC` | |
| `ACCOUNT-001` | 등록 계좌 목록 조회 | `GET` | `/accounts` | `O` | `USER \| MERCHANT` | 현재 세션의 `partyId` 기준 |
| `ACCOUNT-002` | 계좌 추가 | `POST` | `/accounts` | `O` | `USER \| MERCHANT` | 현재 세션의 `partyId` 기준 |
| `ACCOUNT-003` | 계좌 삭제 | `DELETE` | `/accounts/{accountId}` | `O` | `USER \| MERCHANT` | 본인 계좌만 삭제 |
| `ACCOUNT-004` | 주거래 계좌 변경 | `PATCH` | `/accounts/{accountId}/primary` | `O` | `USER \| MERCHANT` | 본인 계좌만 변경 |
| `PAY-001` | QR 가맹점 정보 조회 | `GET` | `/merchants/{merchantPartyId}` | `O` | `USER` | QR 스캔 후 결제 플로우 진입 |
| `PAY-002` | 결제 실행 | `POST` | `/payment` | `O` | `USER` | 소비자 전용 |
| `CHARGE-001` | 충전 정보 조회 | `GET` | `/charges/{partyId}/init` | `O` | `USER` | 충전 한도·할인 계산 포함 |
| `CHARGE-002` | 충전 실행 | `POST` | `/charges` | `O` | `USER` | 소비자 전용 |
| `EXCHANGE-001` | 환전 정보 조회 | `GET` | `/exchange/{partyId}/init` | `O` | `USER \| MERCHANT` | 환전 가능 여부·예정 금액 포함 |
| `EXCHANGE-002` | 환전 실행 | `POST` | `/exchange` | `O` | `USER \| MERCHANT` | 서비스 용어는 환전 |
| `MERCHANT-001` | 가맹점 매출 요약 조회 | `GET` | `/merchant/dashboard` | `O` | `MERCHANT` | 가맹점 전용 |
| `MERCHANT-002` | 가맹점 결제 내역 조회 | `GET` | `/merchant/payments` | `O` | `MERCHANT` | 가맹점 전용 |
| `MERCHANT-003` | 가맹점 결제 상세 조회 | `GET` | `/merchant/payments/{paymentId}` | `O` | `MERCHANT` | |
| `MERCHANT-004` | 결제 취소 | `POST` | `/merchant/payments/{paymentId}/cancel` | `O` | `MERCHANT` | 시간 제한 없음 |
| `MERCHANT-005` | 가맹점 정산 내역 조회 | `GET` | `/merchant/settlements` | `O` | `MERCHANT` | 현재 가맹점의 `EXCHANGE` 거래 조회 (`transaction.from_party_id = partyId`) |
| `MERCHANT-006` | 가맹점 정산 신청 조회 | `GET` | `/merchant/redeem` | `O` | `MERCHANT` | 토큰→현금 |
| `MERCHANT-007` | 가맹점 정산 신청 실행 | `POST` | `/merchant/redeem` | `O` | `MERCHANT` | 토큰→현금 |
| `MERCHANT-008` | 가맹점 QR 생성/조회 | `GET` | `/merchants/qr` | `O` | `MERCHANT` | 결제용 QR 코드 (merchantId 포함) |
| `MERCHANT-009` | 가맹점 마이페이지 조회 | `GET` | `/merchant/mypage` | `O` | `MERCHANT` | |
| `MERCHANT-010` | 가맹점 계좌 변경 | `PATCH` | `/merchant/accounts` | `O` | `MERCHANT` | SETTLEMENT 계좌 upsert |
| `MY-001` | 사용자 마이페이지 조회 | `GET` | `/users/profile` | `O` | `USER` | 소비자 전용 |
| `MY-002` | 사용자 내역 조회 | `GET` | `/users/histories` | `O` | `USER` | 소비자 전용 |
| `MY-003` | 내역 상세 조회 | `GET` | `/users/histories/{partyId}` | `O` | `USER` | 소비자 전용 |
| `WALLET-001` | 잔액 조회 | `GET` | `/wallet/balance` | `O` | `USER \| MERCHANT` | 역할별 서비스/응답 분리 가능 |
