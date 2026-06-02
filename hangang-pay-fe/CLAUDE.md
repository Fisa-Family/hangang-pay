# hangang-pay-fe

한강페이 지역화폐 거래를 위한 모바일 웹 SPA. 사용자는 지역화폐 잔액 조회, QR 결제, 거래 내역 조회, 계좌 관리를 수행하고, 가맹점은 홈 대시보드와 결제 QR을 확인한다.

## 작업 원칙

- 화면 구현 전 `docs/dev-specs`의 디자인 시스템과 화면 명세를 확인한다.
- API 연동 전 `../hangang-pay-be/docs/rest_api.md`와 실제 백엔드 코드를 확인한다.
- 모바일 웹 기준으로 구현한다.
- 데스크톱 브라우저에서는 앱을 모바일 최대 폭으로 중앙 정렬해 폰 화면처럼 보여준다.
- 요청 범위를 벗어난 리팩터링, 디자인 변경, 라우트 변경은 하지 않는다.
- `hangang-pay-bc` 또는 블록체인 모듈 코드를 FE 작업 중 생성하거나 수정하지 않는다.

## 기술 스택

- React 19
- TypeScript
- Vite
- React Router
- TanStack Query
- Tailwind CSS v4
- Zustand
- Vitest 설정은 존재하지만 현재는 새 테스트를 작성하지 않는다.

## 주요 디렉터리

```
src/
├── App.tsx                  # RouterProvider 연결
├── main.tsx                 # 앱 엔트리
├── app/                     # router, provider, root error
├── routes/                  # layout, auth/role guard
├── auth/                    # 현재 사용자 조회 및 인증 타입
├── api/                     # API client, endpoint 함수, 응답 타입
├── pages/                   # route 단위 화면
├── components/common/       # 재사용 공통 컴포넌트
├── lib/                     # 순수 유틸
└── test/                    # 테스트 설정
```

문서:

- `docs/dev-specs/design-system.md`: 색상, 타이포그래피, 레이아웃 기준
- `docs/dev-specs/screens/*.md`: 화면별 route, 액션, 상태 분기 기준
- `../hangang-pay-be/docs/rest_api.md`: API 목록과 endpoint 1차 확인

## 라우팅

라우트 정의는 `src/app/router.tsx`가 소유한다.

현재 주요 화면:

- Public: `/login`
- User: `/home`, `/mypage`, `/mypage/payments`, `/mypage/accounts`, `/mypage/accounts/add`
- User payment flow: `/pay/scan`, `/pay/amount/:merchantId`, `/pay/confirm`, `/pay/pin`, `/pay/processing`, `/pay/complete`
- Merchant: `/merchant/home`, `/merchant/qr`, `/merchant/payments`, `/merchant/mypage`

인증과 권한 가드는 `src/routes/guards.tsx`에서 관리한다. 개발 중 인증 우회 로직은 임시 코드이므로 실제 인증 API가 확정되면 제거한다.

## 화면 구현 지침

- page 컴포넌트는 route 진입, API 호출, 상태 분기, 화면 이동을 담당한다.
- 공통 컴포넌트는 표시, 입력, 피드백처럼 재사용 가능한 UI 역할만 담당한다.
- loading, empty, error 상태를 명시적으로 처리한다.
- 결제, 계좌, 인증 등 민감 플로우에서는 PIN, 인증번호, 세션 정보 등 민감 값을 URL에 노출하지 않는다.
- 처리중 화면은 서버 요청 대기 상태만 표시한다. 실패 시 처리중 화면에 머무르지 말고 직전 입력/확인 화면으로 돌아가 실패 사유를 표시한다.
- 화면 UX와 상태 분기는 `docs/dev-specs/screens/*.md`를 우선한다.

## 컴포넌트 재사용 규칙

새 UI를 구현하기 전에 반드시 `src/components/common`을 먼저 확인한다.

- 이미 존재하는 공통 컴포넌트가 있으면 새로 만들지 않고 재사용한다.
- 동일하거나 유사한 UI 패턴을 2곳 이상에서 인라인으로 구현하지 않는다. 반복되면 공통 컴포넌트로 추출한다.
- 공통 컴포넌트는 API endpoint, DTO, query key, route 경로를 직접 알지 않는다.
- 화면 이동과 서버 요청은 page 컴포넌트가 담당한다.
- 공통 컴포넌트를 수정할 때는 모든 사용처가 깨지지 않는지 확인한다.

### 구현 전 공통 컴포넌트 확인 절차

새 화면 또는 UI를 구현하기 전에는 반드시 다음 순서로 진행한다.

1. `src/components/common/index.ts`를 확인한다.
2. 구현하려는 UI 요소를 기존 공통 컴포넌트와 매핑한다.
3. 코드 수정 전, 사용할 공통 컴포넌트를 짧게 명시한다.
   - 예: "이 화면은 `AppShell`, `PageHeader`, `TextField`, `Button`을 사용한다."
4. 기존 공통 컴포넌트로 표현 가능한 UI는 직접 `<button>`, `<input>`, `<header>` 등을 새로 만들지 않는다.
5. 공통 컴포넌트의 props가 부족해 화면 요구사항을 맞추기 어렵다면, 먼저 공통 컴포넌트 확장을 검토한다.
6. 탭, 링크성 텍스트 버튼처럼 아직 공통 컴포넌트가 없는 패턴은 page 내부에 구현할 수 있다. 같은 패턴이 2회 이상 반복되면 공통 컴포넌트로 추출한다.

### 화면 구현 체크리스트

코드 수정 전 확인한다.

- `src/components/common/index.ts`의 export 목록을 확인했는가?
- 구현하려는 UI 요소를 기존 공통 컴포넌트와 매핑했는가?
- 사용할 공통 컴포넌트를 작업 전 명시했는가?
- 기존 공통 컴포넌트로 가능한 UI를 직접 `<button>`, `<input>`, `<header>` 등으로 새로 만들고 있지 않은가?
- 공통 컴포넌트 props가 부족하다면 page에서 우회 구현하기 전에 공통 컴포넌트 확장을 검토했는가?
- 새 공통 컴포넌트를 추가했다면 `src/components/common/index.ts`에서 export했는가?
- 공통 컴포넌트를 추가, 삭제, 이름 변경했다면 이 문서의 빠른 목록을 업데이트했는가?
- 공통 컴포넌트의 사용법이 바뀌었다면 관련 사용처를 함께 확인했는가?

### 공통 컴포넌트 빠른 목록

정확한 전체 목록, props, 동작은 `src/components/common/index.ts`와 실제 구현(`src/components/common`)을 기준으로 확인한다. 이 목록은 빠른 탐색용이므로 공통 컴포넌트를 추가, 삭제, 이름 변경할 때 함께 갱신한다.

- Layout: `AppShell`, `PageHeader`, `BackTitleHeader`, `BottomNav`
- Action/Input: `Button`, `TextField`, `SelectField`, `CheckboxGroup`, `AmountInput`, `NumberPad`, `PinCodeInput`
- Feedback: `ConfirmDialog`, `Toast`, `EmptyState`, `ErrorBoundary`, `ProcessingState`, `ResultState`, `StatusBadge`
- List/Card: `ListItem`, `AccountRow`, `BalanceCard`, `SummaryCard`, `SettingsMenuCard`, `UserProfileCard`
- History: `HistoryEntryCard`, `HistoryListItem`, `HistoryDateGroupHeader`, `HistoryTypeTabs`
- Icons: `BackspaceIcon`, `ChevronRightIcon`

## API 연동 지침

- API 호출 함수는 `src/api`에 작성한다.
- page 컴포넌트에서 endpoint 문자열, request/response 변환 로직을 직접 작성하지 않는다.
- 공통 fetch 처리는 `src/api/client.ts`의 `apiFetch`와 `ApiError` 방식을 따른다.
- API 목록과 endpoint는 `../hangang-pay-be/docs/rest_api.md`를 1차로 확인한다.
- 단, `rest_api.md`에는 요청/응답 DTO와 에러 코드가 최신 상태로 모두 명시되어 있지 않을 수 있다.
- 실제 request/response 구조, enum, success code, error code는 반드시 `../hangang-pay-be/src/main/java`의 Controller, Request/Response DTO, Service, exception/code 정의를 확인한 뒤 맞춘다.
- BE 명세 문서와 실제 백엔드 코드가 충돌하면 실제 백엔드 코드를 우선한다.

### 백엔드 확인 위치

- Controller: endpoint, method, path variable, query param 확인
- Request DTO: request body 필드명과 타입 확인
- Response DTO: `result` 내부 응답 구조 확인
- Enum: 상태값, 거래유형, 역할 등 문자열 값 확인
- Exception/ErrorCode/SuccessCode: FE에 표시할 code/message 분기 확인

## 상태 관리

- 서버에서 읽어오는 데이터와 서버 변경 요청은 TanStack Query로 관리한다.
- 클라이언트 전용 공유 상태는 Zustand로 관리한다.
- 한 화면 안에서만 쓰는 단순 입력값은 `useState`를 사용한다.
- 서버 응답 데이터를 Zustand에 복제하지 않는다.
- URL에 노출되면 안 되는 PIN, 인증번호, 민감 정보는 query param에 넣지 않는다.

## 디자인 지침

- 디자인 기준은 `docs/dev-specs/design-system.md`와 `src/index.css`의 토큰을 따른다.
- 모바일 터치 영역을 우선한다.
- 데스크톱 전용 레이아웃을 새로 만들지 않는다.
- Tailwind CSS v4를 사용한다. CSS Modules나 인라인 스타일은 기존 패턴상 필요한 경우에만 제한적으로 사용한다.
- 화면별 예외는 `docs/dev-specs/screens/*.md`를 우선한다.
- 색상은 반드시 `src/index.css`에 정의된 CSS 변수 토큰(`bg-background`, `bg-card`, `text-foreground` 등)을 사용한다. `#rrggbb` 또는 `rgb()` 형태의 임의 색상값은 사용하지 않는다. 토큰에 없는 색이 필요하다면 먼저 `src/index.css`를 확인해 가장 가까운 토큰을 찾는다.

## 테스트 방침

현재 FE 작업에서는 테스트 코드를 새로 작성하지 않는다.

- 기능 구현 및 수정 시 화면 동작 확인, 타입 체크, 빌드 확인을 우선한다.
- 기존 테스트 파일이 있더라도 새 기능에 맞춰 테스트를 추가하지 않는다.
- 테스트 코드 작성이 꼭 필요하다고 판단되는 경우에는 먼저 팀과 논의한 뒤 진행한다.
- PR 전 검증은 기본적으로 `npm run build`와 수동 화면 확인을 기준으로 한다.

## 커밋 전 확인

커밋 전에는 반드시 다음을 실행한다.

```bash
npm run fix
npm run build
```

- `npm run fix`: Prettier 포맷팅과 자동 수정 가능한 ESLint 이슈 정리
- `npm run build`: TypeScript 타입 체크와 프로덕션 빌드 확인
