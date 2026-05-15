# Design System

한강페이는 모바일 금융 앱이다. 화면은 차분하고 신뢰감 있게 만들고, 주요 강조색은 파란색을 사용한다.

## Base

- Font: Pretendard Variable
- App width: 모바일 전체 폭, 데스크톱에서는 최대 430px 중앙 정렬
- Radius: 기본 8px
- Touch target: 최소 44px
- Amount: `10,000원` 형식, 우측 정렬

## Colors

실제 값은 `src/index.css`의 CSS 변수와 Tailwind theme token을 기준으로 한다.

| Token              | Usage                        |
| ------------------ | ---------------------------- |
| `primary`          | 주요 CTA, 활성 탭, 핵심 강조 |
| `secondary`        | 보조 버튼, 약한 강조 영역    |
| `background`       | 앱 기본 배경                 |
| `surface`, `card`  | 카드, 입력 그룹, 모달        |
| `foreground`       | 기본 텍스트                  |
| `muted-foreground` | 보조 텍스트                  |
| `border`, `input`  | 구분선, 입력 테두리          |
| `destructive`      | 삭제, 실패, 위험 액션        |
| `success`          | 완료, 성공 상태              |
| `warning`          | 주의 상태                    |
| `info`             | 안내 상태                    |

## Rules

- 한 화면의 핵심 버튼만 `primary`를 사용한다.
- 위험 액션은 `destructive`를 사용하고 확인 단계를 둔다.
- 상태는 색상만으로 표현하지 않고 텍스트를 함께 표시한다.
- 데스크톱 전용 레이아웃을 만들지 않는다.
- 콘텐츠를 데스크톱 폭에 맞춰 늘리지 않는다.
