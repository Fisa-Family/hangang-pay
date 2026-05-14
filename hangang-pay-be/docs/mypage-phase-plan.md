# Mypage API Temporary Phase Plan

## Summary
- 목적: `78 -> 82 -> 80 -> 81 -> 84` 순서로 마이페이지 API를 안전하게 개발하기 위한 세션 간 기준 문서.
- 운영 원칙: 각 티켓은 독립 브랜치/PR로 진행하되, 선행 티켓 산출물을 다음 티켓의 입력으로 고정.
- 문서 수명: 임시 문서로 사용 후 최종 통합 머지 직전에 삭제.

## Phase Plan
1. **Phase 1 - HANGANG-78 (마이페이지 조회 API 계약 고정)**
- 산출물:
  - `ApiResponse` 기존 규격 고정 (`isSuccess/status/code/message/result`)
  - 마이페이지 조회 응답 DTO 정의
    - `UserProfileResponse` (`name`, `createdAt`)
    - `UserPaymentHistoryResponse`
    - `UserPaymentHistoryListResponse`
  - `GlobalExceptionHandler` 매핑 정리
    - `BusinessException` 실패 응답 일관화
    - Validation 실패 시 `result.fieldErrors` 반환
- 제외 항목:
  - Request DTO 선행 도입
  - `ErrorResponse` 신규 클래스 도입
  - `ErrorResponse` 전용 직렬화 테스트
- 테스트:
  - `ApiResponse` 성공/실패 직렬화 테스트
  - 조회 응답 DTO 직렬화 테스트
  - validation 실패 응답 테스트
  - `BusinessException` 매핑 테스트
- 완료 기준(DoD):
  - MY-001/MY-002 선행 계약(성공/실패 JSON)이 테스트로 고정됨
  - 예외 응답이 공통 포맷으로 일관 반환됨

2. **Phase 2 - HANGANG-82 (API 예외 및 에러 처리 로직)**
- 산출물: `ErrorCode`, `BusinessException`, `GlobalExceptionHandler` 매핑 구현.
- 테스트: validation 예외 매핑, 인증/권한/데이터 없음 등 비즈니스 예외 매핑.
- 완료 기준(DoD): 예외 발생 시 항상 `ApiResponse` 공통 포맷(`isSuccess/status/code/message/result`)으로 일관 응답.

3. **Phase 3 - HANGANG-80 (프로필 조회 API 설계/구현)**
- 산출물: `GET /api/v1/users/profile` (인증 사용자 본인 기준 조회), 응답 DTO 매핑.
- 테스트: 정상 조회, 인증 없음/권한 오류, 프로필 없음 예외 케이스.
- 완료 기준(DoD): 프로필 API 단위 테스트 GREEN + Swagger 기본 문서 반영.

4. **Phase 4 - HANGANG-81 (결제 내역 조회 API 설계/구현)**
- 산출물: `GET /api/v1/users/payments` (기본 `page/size`, 최신순), 인증 사용자 본인 내역 조회.
- 테스트: 빈 내역 응답, 페이징/정렬, 권한/인증 예외.
- 완료 기준(DoD): 결제내역 API 단위 테스트 GREEN + 응답 계약 일치.

5. **Phase 5 - HANGANG-84 (API 문서화 및 Swagger 연동)**
- 산출물: 마이페이지 태그, 프로필/결제내역 API 설명, 요청 파라미터/응답 DTO/에러 예시 문서화.
- 테스트/검증: Swagger UI에서 인증 필요 표기/응답 예시 확인.
- 완료 기준(DoD): `docs/rest_api.md` + Swagger 설명이 실제 구현과 일치.

## Public Interface/Contract Decisions
- 공통 응답은 `ApiResponse` 현재 규격 유지:
  - `{"isSuccess": boolean, "status": string, "code": string, "message": string, "result": object|null}`
- 마이페이지 경로:
  - `GET /api/v1/users/profile`
  - `GET /api/v1/users/payments`
- 결제내역 페이징:
  - 우선 `page/size` 표준 사용(커서는 후속 논의 항목으로 분리)
- 데이터 없음 정책:
  - 결제내역은 `200 + empty list`
  - 프로필은 도메인 정책에 맞는 명시 예외 처리
- 프로필 표시 정책:
  - 화면 노출 필드는 `name`, `createdAt`만 사용
  - `name`은 내부적으로 `User.nickname`을 매핑해 제공

## Test Plan by Phase
- `78`: `ApiResponse`/조회 DTO 직렬화 + validation/예외 매핑 테스트(RED -> GREEN).
- `82`: 예외 매핑 테스트(RED -> GREEN).
- `80`: 프로필 API 정상/예외 테스트(RED -> GREEN).
- `81`: 결제내역 빈목록/페이징/권한 테스트(RED -> GREEN).
- `84`: 문서 정합 검증(엔드포인트/파라미터/에러예시 교차 확인).

## Assumptions
- 핵심 공통부 머지 전에도 `78` 범위의 계약/테스트 초안은 선행 작성 가능.
- 인증은 세션 기반 유지(JWT 전환 없음).
- 이 문서는 임시 운영 문서이며 최종 통합 시 삭제한다.
