# 계좌 API 구현 설명

이 문서는 현재 `AccountController`와 계좌 서비스 구현 기준이다.

## 서버 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

프로필별 DB 접속 정보는 환경변수로 주입한다.

| 파일 | 용도 | 비고 |
| --- | --- | --- |
| `application.yaml` | 공통 설정 | SpringDoc, Actuator, CORS fallback, blockchain/wallet 기본값 |
| `application-dev.yml` | 개발 DB 설정 | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 필요 |
| `application-prod.yml` | 운영 DB 설정 | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `WALLET_KEY_CIPHER_SECRET` 필요 |
| `application-test.yml` | 테스트 설정 | H2, `bank.base-url=http://localhost:8081` |

`BankClient`는 `bank.base-url` 설정을 사용한다. 테스트 프로필에는 값이 있고, dev/prod 실행 시에도 같은 키를 제공해야 한다.

## 현재 인증 상태

계좌 API는 아직 임시로 `partyId`를 query parameter로 받는다.

```text
GET    /api/v1/accounts?partyId=1
POST   /api/v1/accounts?partyId=1
DELETE /api/v1/accounts/{accountId}?partyId=1
PATCH  /api/v1/accounts/{accountId}/primary?partyId=1
```

현재 코드 기준:

- `AccountController`는 `@RequestParam Long partyId`를 사용한다.
- `SecurityConfig`는 `/api/v1/accounts/**`를 `permitAll()`로 열어 둔다.
- 로그인 세션 기반으로 전환할 때는 `@SessionAttribute(SessionAttributeNames.PARTY_ID)`를 사용한다.
- 세션 전환 후에는 `SecurityConfig`의 `/api/v1/accounts/** permitAll()`도 제거하고 `USER | MERCHANT` 권한으로 정렬한다.

## ACCOUNT-001: 등록 계좌 목록 조회

현재 party의 모든 계좌를 조회한다. 계좌번호는 뒤 4자리만 노출하고 나머지는 `*`로 마스킹한다.

```http
GET /api/v1/accounts?partyId=1
```

응답 result:

```json
{
  "accounts": [
    {
      "accountId": 1,
      "institutionCode": "088",
      "bankName": "신한은행",
      "maskedAccountNumber": "******3210",
      "accountType": "PRIMARY"
    }
  ],
  "totalCount": 1
}
```

## ACCOUNT-002: 계좌 추가

BE `institution` 캐시에서 기관을 조회하고, bank 서버의 은행 원장 계좌 존재 여부를 확인한 뒤 계좌를 등록한다.

현재 구현 기준:

- 요청의 `institutionCode`로 BE `institution`을 조회한다.
- `BankClient.getBankAccount(institution.id, accountNumber)`로 bank 서버 계좌 존재 여부를 확인한다.
- 같은 party의 동일 계좌번호 중복 등록을 막는다.
- party당 계좌는 최대 3개까지 등록 가능하다.
- 회원가입 시 주계좌가 생성된다는 전제라서 추가 계좌는 `SECONDARY`로 저장한다.

```http
POST /api/v1/accounts?partyId=1
Content-Type: application/json

{
  "institutionCode": "088",
  "accountNumber": "9876543210"
}
```

응답 result:

```json
{
  "accountId": 2,
  "institutionCode": "088",
  "bankName": "신한은행",
  "maskedAccountNumber": "******3210",
  "accountType": "SECONDARY"
}
```

## ACCOUNT-003: 계좌 삭제

`accountId`와 `partyId`를 함께 조회해 본인 계좌인지 검증한 뒤 삭제한다.

삭제 제한:

- 마지막 계좌는 삭제할 수 없다.
- `PRIMARY` 계좌는 삭제할 수 없다.
- `PRIMARY`를 삭제하려면 먼저 `ACCOUNT-004`로 주거래 계좌를 변경한다.

```http
DELETE /api/v1/accounts/3?partyId=1
```

## ACCOUNT-004: 주거래 계좌 변경

지정한 계좌를 `PRIMARY`로 변경한다. 기존 `PRIMARY` 계좌가 있으면 `SECONDARY`로 전환한다.

```http
PATCH /api/v1/accounts/2/primary?partyId=1
```

응답 result:

```json
{
  "accountId": 2,
  "accountType": "PRIMARY",
  "previousPrimaryAccountId": 1
}
```

대상이 이미 `PRIMARY`인 경우 `previousPrimaryAccountId`는 `null`이다.

## 가맹점 정산 계좌 변경

가맹점 정산 계좌 변경은 `AccountCommandService`에 구현되어 있지만, API는 가맹점 도메인에 노출된다.

```http
PATCH /api/v1/merchant/accounts
Content-Type: application/json

{
  "institutionCode": "088",
  "accountNumber": "9876543210"
}
```

현재 세션의 `partyId` 기준으로 `SETTLEMENT` 계좌를 upsert한다.
