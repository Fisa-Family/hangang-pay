# 계좌 API 구현 설명

---

## 서버 실행

```
./gradlew bootRun --args='--spring.profiles.active=local'
```

`--spring.profiles.active=local`이 필요한 이유는 설정 파일이 두 개로 분리되어 있기 때문입니다.

| 파일 | 용도 | git 커밋 |
| --- | --- | --- |
| application.yaml | 공통 설정 | O |
| application-local.yaml | DB 접속 정보, 비밀번호 등 | X (gitignored) |

`local` 프로파일을 지정해야 `application-local.yaml`을 함께 로드해 DB에 연결됩니다.
지정하지 않으면 DB 접속 정보가 없어 서버가 시작되지 않습니다.

---

## 임시 인증 처리 안내

LOGIN-001, LOGIN-002 미구현으로 세션 인증 블록을 주석 처리하고,
`partyId`를 쿼리 파라미터로 직접 전달하는 방식으로 임시 동작 중입니다.

로그인 구현 후 `AccountController`의 각 메서드에서 아래 두 가지를 처리합니다.

1. `@RequestParam Long partyId` → `HttpSession session` 파라미터로 교체
2. 주석 처리된 세션 인증 블록 주석 해제

세션 키는 `partyId`이며, 로그인 시 `session.setAttribute("partyId", party.getId())`로 저장합니다.

---

## ACCOUNT-001: 등록 계좌 목록 조회

세션의 partyId 기준으로 본인 계좌 목록을 반환합니다.
계좌번호는 뒤 4자리만 노출하고 나머지는 마스킹 처리합니다.

**임시 테스트 URL**

```
GET http://localhost:8080/api/v1/accounts?partyId=1
```

| 파라미터 | 설명 |
| --- | --- |
| partyId=1 | 테스트용 party.id, 로그인 구현 후 제거 |

---

## ACCOUNT-002: 계좌 추가

은행 원장(bank_account 테이블) 조회 후 예금주명을 대조하고 계좌를 등록합니다.
최대 3개까지 등록 가능하며, 첫 번째 계좌는 자동으로 PRIMARY로 설정됩니다.
1원 인증은 AUTH-003, AUTH-004 구현 이후 연동 예정입니다.

**임시 테스트 URL**

```
POST http://localhost:8080/api/v1/accounts?partyId=1
Content-Type: application/json

{
  "institutionCode": "088",
  "accountNumber": "9876543210"
}
```

| 파라미터 | 설명 |
| --- | --- |
| partyId=1 | 테스트용 party.id, 로그인 구현 후 제거 |

**테스트 데이터 삽입 SQL**

institution, bank_account 테이블에 데이터가 없으면 BANK_ACCOUNT_NOT_FOUND가 발생합니다.
아래 SQL을 순서대로 실행한 뒤 테스트합니다.

```sql
INSERT INTO institution (institution_code, institution_name, created_at, updated_at)
VALUES ('004', '국민은행', NOW(), NOW()),
       ('088', '신한은행', NOW(), NOW());

-- institution_id는 위 INSERT 후 SELECT id FROM institution WHERE institution_code = '088' 로 확인
INSERT INTO bank_account (institution_id, account_number, balance, created_at, updated_at)
VALUES (1, '1234567891234', 0, NOW(), NOW()),
       (2, '9876543210', 0, NOW(), NOW());

-- party가 없으면 삽입
INSERT INTO party (party_type, created_at, updated_at)
VALUES ('USER', NOW(), NOW());
```

---

## ACCOUNT-003: 계좌 삭제

accountId와 partyId를 함께 조회해 본인 계좌인지 검증 후 삭제합니다.
주거래 계좌(PRIMARY)와 마지막 계좌는 삭제할 수 없습니다.
PRIMARY 계좌를 삭제하려면 먼저 ACCOUNT-004로 주거래 계좌를 변경해야 합니다.

**임시 테스트 URL**

```
DELETE http://localhost:8080/api/v1/accounts/3?partyId=1
```

| 파라미터 | 설명 |
| --- | --- |
| 3 | ACCOUNT-002 응답에서 받은 accountId |
| partyId=1 | 테스트용 party.id, 로그인 구현 후 제거 |

---

## ACCOUNT-004: 주거래 계좌 변경

지정한 계좌를 PRIMARY로 변경합니다. 기존 PRIMARY 계좌는 자동으로 SECONDARY로 전환됩니다.
대상이 이미 PRIMARY인 경우 previousPrimaryAccountId는 null로 반환됩니다.

**임시 테스트 URL**

```
PATCH http://localhost:8080/api/v1/accounts/2/primary?partyId=1
```

| 파라미터 | 설명 |
| --- | --- |
| 2 | 주거래로 변경할 accountId |
| partyId=1 | 테스트용 party.id, 로그인 구현 후 제거 |
