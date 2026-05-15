# FE Dev Specs

이 디렉터리는 한강페이 프론트엔드 화면 구현 명세다. UI는 모바일 웹 SPA 기준으로 설계한다.
데스크톱 브라우저에서도 확인할 수 있어야 하며, 이때는 데스크톱 전용 레이아웃을 만들지 않고 모바일 화면 폭을 중앙 정렬해 폰 화면처럼 보여준다.

## Read Order

1. `../../CLAUDE.md` - FE 전체 기술 스택, 상태 관리, 라우팅, 테스트 원칙
2. `design-system.md` - 색상, 폰트, 레이아웃 스타일 기준
3. `components.md` - 여러 화면에서 재사용되는 공통 컴포넌트 명세
4. `screens/*.md` - 화면별 UI, 경로, 액션, 상태 명세
5. `../../../hangang-pay-be/docs/rest_api.md` - 구현 시 사용할 백엔드 API 명세

## Scope

- 이 문서들은 화면 구조, 사용자 액션, 화면 전환, 상태별 UI를 정의한다.
- 화면 경로는 각 `screens/*.md`의 `경로` 항목을 기준으로 한다.
- 시각 스타일은 `design-system.md`와 `src/index.css`의 토큰을 기준으로 한다.
- 공통 컴포넌트의 역할과 props는 `components.md`를 기준으로 한다.
- 백엔드 API ID, endpoint, request/response DTO는 이 디렉터리에 중복 기재하지 않는다.
- API 연동이 필요한 작업은 반드시 `../../../hangang-pay-be/docs/rest_api.md`를 열어 현재 백엔드 명세를 확인한 뒤 구현한다.
- BE 명세와 FE 화면 명세가 충돌하면, API 계약은 BE 명세를 우선하고 화면 UX는 이 디렉터리의 `screens/*.md`를 우선한다.

## Implementation Notes

- 화면 ID는 컴포넌트명, route 설정, test id를 맞출 때 기준으로 사용한다.
- 접근 권한은 화면 ID prefix와 경로 기준으로 판단한다. `A-*`, `U-REG-*`, `M-REG-*`는 public, `U-*`와 `U-MY-*` 등 사용자 영역은 user, `M-*` 가맹점 영역은 merchant, `C-*` 공통 화면은 any로 본다.
- `U-PAY-02`는 가맹점 확인과 금액 입력을 함께 처리한다. `U-PAY-03`을 별도 화면으로 구현하지 않는다.
- `C-*` 중 `modal` 타입은 별도 route를 만들지 않고 부모 화면 위 오버레이로 구현한다.
- 처리중 화면의 성공/실패 전환은 실제 구현 시 연결한 서버 요청 결과를 기준으로 한다.
- 카메라 권한 설정은 모바일 웹 브라우저 UX로 처리한다. React Native API를 사용하지 않는다.
- 데스크톱 viewport에서는 앱 루트에 모바일 최대 폭을 적용하고 화면을 중앙 정렬한다. 콘텐츠를 데스크톱 폭에 맞춰 늘리지 않는다.
- 화면 문구, 검증 조건, 금액 제한은 `screens/*.md`에 명시된 내용만 사용한다. 명세에 없는 안내 문구나 제약사항을 임의로 추가하지 않는다.

## What Not To Do

- 이 디렉터리의 화면 명세에 API ID, endpoint, DTO를 추가하지 않는다.
- 백엔드 API 변경사항을 FE 화면 문서에 복사하지 않는다.
- 공통 컴포넌트 구조를 `components.md`와 충돌하게 임의로 새로 정의하지 않는다.
- 데스크톱 전용 레이아웃을 추가하지 않는다.
