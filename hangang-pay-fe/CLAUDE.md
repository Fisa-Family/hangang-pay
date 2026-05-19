# blockchain-fe

React + Vite 기반 대시보드. `blockchain-be`의 REST API를 호출하여 시나리오 실행 + 거래 추적 시각화.

루트 `CLAUDE.md`도 함께 참조.

## 모듈 책임

1. 사용자/은행 드롭다운 선택 (로그인 없음)
2. CBDC 발급, 자행이체, 타행이체 시나리오 실행 UI
3. 거래 리스트 + 단일 거래 상세 페이지 (단계별 타임라인)
4. 잔액 실시간 조회

## 기술 스택

- React (Vite 기본 버전)
- JavaScript (TypeScript 아님)
- Vite dev server (포트 5173)
- Flat ESLint config (`eslint.config.js`)

## 디자인 톤

**깔끔한 모노톤 (monochrome).**

- 흰 배경 또는 매우 옅은 회색
- 검은색/짙은 회색 텍스트
- 강조는 회색 단계로만 (회색 톤 차이)
- 컬러는 최소화 (상태 표시 시에만 미세하게)
- 폰트는 sans-serif 시스템 폰트
- 카드/박스는 옅은 회색 테두리 또는 그림자 한 줄
- 둥근 모서리는 작게 (4-6px)
- 여백은 충분히 (학습용 데모, 가독성 우선)

CSS 라이브러리는 별도 도입 X. CSS Modules 또는 인라인 스타일.

## 페이지 구조

```
src/
├── App.jsx                       # 라우터 + 레이아웃
├── main.jsx
│
├── pages/
│   ├── Dashboard.jsx             # 전체 현황 (사용자 잔액, 최근 거래)
│   ├── CBDCIssuance.jsx          # 한국은행 CBDC 발급 시나리오
│   ├── IntraBankTransfer.jsx     # 자행이체 시나리오
│   ├── InterBankTransfer.jsx     # 타행이체 시나리오
│   ├── TransactionList.jsx       # 거래 리스트 (필터, 페이징)
│   ├── TransactionDetail.jsx     # 단일 거래 상세 (타임라인 시각화)
│   └── Users.jsx                 # 사용자 목록 + 잔액
│
├── components/
│   ├── Layout.jsx                # 사이드바 + 헤더 + 본문
│   ├── BankSelector.jsx          # 은행 드롭다운
│   ├── UserSelector.jsx          # 사용자 드롭다운
│   ├── BalanceCard.jsx           # 잔액 카드
│   ├── TimelineView.jsx          # 단계별 타임라인 (수평 막대 차트)
│   ├── TxHashCopy.jsx            # 트랜잭션 해시 복사 컴포넌트
│   └── StatusBadge.jsx           # 거래 상태 뱃지
│
├── api/
│   ├── client.js                 # fetch 또는 axios 기본 설정
│   ├── cbdc.js
│   ├── transfer.js
│   ├── tracking.js
│   ├── users.js
│   └── banks.js
│
├── hooks/
│   ├── useUsers.js               # 사용자 목록 조회
│   ├── useBanks.js
│   └── useTransactions.js
│
└── styles/
    ├── global.css
    └── tokens.css                # 디자인 토큰 (색상, 여백)
```

## 라우팅

`react-router-dom` 사용 권장. 또는 단순 상태 기반 화면 전환.

```
/                      → Dashboard
/cbdc                  → CBDCIssuance
/transfer/intra        → IntraBankTransfer
/transfer/inter        → InterBankTransfer
/transactions          → TransactionList
/transactions/:id      → TransactionDetail
/users                 → Users
```

## 페이지별 핵심 UI

### Dashboard

- 전체 은행 CBDC 잔액 카드 4개 (한국은행, 우리, 신한, 하나)
- 최근 거래 5개 (간단한 테이블)
- 사용자별 예금토큰 잔액 요약

### CBDCIssuance

- 입력: 대상 은행 (드롭다운), 금액
- 버튼: 발급
- 결과: 트랜잭션 해시 표시 + 시간 + correlationId
- 발급 완료 후 자동으로 잔액 갱신

### IntraBankTransfer

- 입력: 은행 선택 → 보내는 사용자 + 받는 사용자 (같은 은행) + 금액
- 버튼: 이체
- 결과: 트랜잭션 해시, 단계별 시간

### InterBankTransfer

- 입력: 보내는 은행 + 사용자, 받는 은행 + 사용자, 금액
- 버튼: 이체
- 결과: 3단계 트랜잭션 해시 모두 표시 + 단계별 시간 + 총 소요시간
- 진행 중에는 단계별 진행 상태 시각화

### TransactionList

- 컬럼: correlationId(짧게), 타입, From, To, 금액, 상태, 소요시간, 시각
- 필터: 거래 타입, 날짜, 은행, 상태
- 페이징
- 행 클릭 → TransactionDetail로

### TransactionDetail

- 거래 기본 정보 (correlationId, 타입, 금액, 상태)
- **TimelineView**: 단계별 막대 차트 (수평 Gantt 스타일)
  - 각 단계: REQUEST_RECEIVED → VALIDATION_DONE → BURN_SUBMITTED → BURN_FINALIZED → ... → RESPONSE_SENT
  - 시간 길이로 막대 표시
  - 각 단계의 tx_hash, block_number, gas_used 표시
- 트랜잭션 해시 복사 가능

### Users

- 9명 사용자 카드/리스트
- 각각: 이름, 소속 은행, 주소(짧게), 예금토큰 잔액
- 검색/필터 (은행별)

## API 호출 패턴

`api/client.js`:

```
const BASE_URL = 'http://localhost:8080/api';
// fetch 기반 헬퍼 (인증 없음)
// 응답 헤더 X-Correlation-Id 추적용으로 저장 가능
```

상태 관리는 별도 라이브러리 없이 React `useState`, `useEffect`로 충분. 필요 시 가벼운 store 도입.

## 디자인 토큰 (tokens.css)

```css
:root {
  --color-bg: #ffffff;
  --color-surface: #fafafa;
  --color-border: #e5e5e5;
  --color-text-primary: #1a1a1a;
  --color-text-secondary: #6b6b6b;
  --color-text-tertiary: #999999;
  --color-accent: #333333; /* 강조도 회색 톤 */
  --color-success: #4a4a4a; /* 성공도 짙은 회색 */
  --color-error: #2a2a2a; /* 에러도 회색 */

  --radius-sm: 4px;
  --radius-md: 6px;

  --spacing-xs: 4px;
  --spacing-sm: 8px;
  --spacing-md: 16px;
  --spacing-lg: 24px;
  --spacing-xl: 32px;

  --font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  --font-mono: 'SF Mono', Menlo, monospace;
}
```

## 실행

```bash
npm run dev       # :5173
npm run build
npm run preview
npm run lint
```

`blockchain-be`가 `:8080`에서 먼저 실행되어야 동작.

## 트러블슈팅 메모

- CORS 이슈: BE에서 `localhost:5173` 허용 (Phase 2에서 설정)
- 잔액이 즉시 갱신 안 됨: 트랜잭션 finality 대기 시간(~2초) 고려
- 트랜잭션 해시는 길어서 UI에선 앞 6자 + ... + 뒤 4자로 단축 표시, 복사는 전체
