# FE Dev Specs

이 디렉터리는 한강페이 프론트엔드 화면 명세를 소유한다.

구현 규칙, 공통 컴포넌트 사용 원칙, API 연동 기준, 상태 관리 기준은 `../../CLAUDE.md`를 따른다.

## Read Order

1. `../../CLAUDE.md` - FE 구현 규칙과 작업 기준
2. `design-system.md` - 화면 시각 기준
3. `screens/*.md` - 화면별 route, 권한, 액션, 상태 명세
4. `../../../hangang-pay-be/docs/rest_api.md` - API 목록과 endpoint 1차 확인

## Scope

- `screens/*.md`는 화면별 route, 권한, 플로우 순서, 액션 결과, 상태 분기, 구현 규칙을 정의한다.
- 화면 route는 각 `screens/*.md`의 `route` 항목을 기준으로 한다.
- 시각 스타일은 `design-system.md`와 실제 `src/index.css` 토큰을 함께 확인한다.
- 백엔드 API ID, endpoint, request/response DTO는 이 디렉터리에 중복 기재하지 않는다.
- API 계약은 백엔드 문서와 실제 백엔드 코드를 우선하고, 화면 UX와 상태 분기는 `screens/*.md`를 우선한다.

## Implementation Notes

- 화면 ID는 참조용 라벨이며 컴포넌트명, route, test id와 반드시 일치할 필요는 없다.
- 접근 권한은 화면 ID prefix와 경로 기준으로 판단한다. `A-*`, `U-REG-*`, `M-REG-*`는 public, `U-*`와 `U-MY-*` 등 사용자 영역은 user, `M-*` 가맹점 영역은 merchant, `C-*` 공통 화면은 any로 본다.
- `U-PAY-01` QR 카메라 처리는 별도 route를 만들지 않는 non-route flow다.
- `U-PAY-02`는 QR로 확인한 가맹점 정보와 결제 금액 입력을 함께 처리한다.
- `C-*` 중 `modal` 타입은 별도 route를 만들지 않고 부모 화면 위 오버레이로 구현한다.
- 검증 조건, 금액 제한, 상태 분기, 화면 전환은 `screens/*.md`에 명시된 계약을 우선한다.

## What Not To Do

- 이 디렉터리의 화면 명세에 API ID, endpoint, DTO를 추가하지 않는다.
- 백엔드 API 변경사항을 FE 화면 문서에 복사하지 않는다.
- 화면 명세에서 공통 컴포넌트 props나 내부 구조를 임의로 새로 정의하지 않는다.
