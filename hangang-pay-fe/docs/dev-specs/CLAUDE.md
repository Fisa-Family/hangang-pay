# FE Dev Specs

이 디렉터리는 한강페이 프론트엔드 화면 구현 명세다. UI는 모바일 웹 SPA 기준으로 설계한다.
데스크톱 브라우저에서도 확인할 수 있어야 하며, 이때는 데스크톱 전용 레이아웃을 만들지 않고 모바일 화면 폭을 중앙 정렬해 폰 화면처럼 보여준다.

## Read Order

1. `../../CLAUDE.md` - FE 전체 기술 스택, 상태 관리, 라우팅, 테스트 원칙
2. `design-system.md` - 색상, 폰트, 레이아웃 스타일 기준
3. `components.md` - 여러 화면에서 재사용되는 공통 컴포넌트 명세
4. `screens/*.md` - 화면별 route, 액션, 상태 명세
5. `../../../hangang-pay-be/docs/rest_api.md` - 구현 시 사용할 백엔드 API 명세

## Scope

- 디자인은 요청에 첨부된 스크린샷을 따른다.
- `screens/*.md`는 화면별 route, 권한, 플로우 순서, 액션 결과, 상태 분기, 구현 규칙을 정의한다.
- 화면 route는 각 `screens/*.md`의 `route` 항목을 기준으로 한다.
- 시각 스타일은 `design-system.md`와 `src/index.css`의 토큰을 기준으로 한다.
- 공통 컴포넌트의 역할과 props는 `components.md`를 기준으로 한다.
- 백엔드 API ID, endpoint, request/response DTO는 이 디렉터리에 중복 기재하지 않는다.
- API 연동이 필요한 작업은 반드시 `../../../hangang-pay-be/docs/rest_api.md`를 열어 현재 백엔드 명세를 확인한 뒤 구현한다.
- BE 명세와 FE 화면 명세가 충돌하면, API 계약은 BE 명세를 우선하고 화면 UX는 이 디렉터리의 `screens/*.md`를 우선한다.

## Implementation Notes

- 화면 ID는 참조용 라벨이며 컴포넌트명, route, test id와 반드시 일치할 필요는 없다.
- 접근 권한은 화면 ID prefix와 경로 기준으로 판단한다. `A-*`, `U-REG-*`, `M-REG-*`는 public, `U-*`와 `U-MY-*` 등 사용자 영역은 user, `M-*` 가맹점 영역은 merchant, `C-*` 공통 화면은 any로 본다.
- `U-PAY-01` QR 카메라 처리는 별도 route를 만들지 않는 non-route flow다.
- `U-PAY-02`는 QR로 확인한 가맹점 정보와 결제 금액 입력을 함께 처리한다.
- `C-*` 중 `modal` 타입은 별도 route를 만들지 않고 부모 화면 위 오버레이로 구현한다.
- 처리중 화면은 loading 전용 화면으로 사용한다. 서버 요청 성공 시 완료 화면으로 이동하고, 실패 시 처리중 화면에 머무르지 않고 직전 입력/확인 화면으로 돌아가 실패 사유를 표시한다.
- 처리 실패 사유는 navigation state, flow store, query param 중 해당 플로우에 맞는 방식으로 전달한다. URL에 민감 정보나 PIN을 노출하지 않는다.
- 카메라 권한 설정은 모바일 웹 브라우저 UX로 처리한다. React Native API를 사용하지 않는다.
- 데스크톱 viewport에서는 앱 루트에 모바일 최대 폭을 적용하고 화면을 중앙 정렬한다. 콘텐츠를 데스크톱 폭에 맞춰 늘리지 않는다.
- 검증 조건, 금액 제한, 상태 분기, 화면 전환은 `screens/*.md`에 명시된 계약을 우선한다.

## What Not To Do

- 이 디렉터리의 화면 명세에 API ID, endpoint, DTO를 추가하지 않는다.
- 백엔드 API 변경사항을 FE 화면 문서에 복사하지 않는다.
- 공통 컴포넌트 구조를 `components.md`와 충돌하게 임의로 새로 정의하지 않는다.
- 데스크톱 전용 레이아웃을 추가하지 않는다.
