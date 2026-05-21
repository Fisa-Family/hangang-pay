# 공통 컴포넌트 명세

이 문서는 여러 화면에서 반복되는 UI 컴포넌트의 역할과 동작 기준을 정의한다. 화면별 문구, 이동 경로, 서버 요청은 각 `screens/*.md`가 소유한다.

## 원칙

- 모바일 웹 기준으로 설계한다.
- 데스크톱 viewport에서는 모바일 최대 폭의 앱 화면을 중앙 정렬한다.
- 컴포넌트는 API endpoint, DTO, query key를 알지 않는다.
- 컴포넌트는 화면 이동을 직접 결정하지 않는다. 라우팅은 page 컴포넌트가 담당한다.
- 컴포넌트는 재사용 가능한 표시/입력/피드백 역할만 가진다.
- 화면별 예외가 있으면 `screens/*.md`의 화면 명세를 우선한다.

---

## Layout

### AppShell

- 타입: layout
- 사용 화면: 전체 page 화면

**역할**

- 앱의 최상위 화면 폭, 배경, safe area, 스크롤 영역을 제공한다.
- 데스크톱 브라우저에서는 모바일 화면을 중앙 정렬해 폰 화면처럼 보여준다.

**Props**
| 이름 | 타입 | 설명 |
|---|---|---|
| children | `ReactNode` | 페이지 본문 |
| bottomNav | `ReactNode?` | 하단 내비게이션 |
| fullBleed | `boolean?` | 카메라 등 전체 화면 콘텐츠 여부 |

**UI**

- 모바일: viewport 전체 폭 사용
- 데스크톱: 모바일 최대 폭 적용, 중앙 정렬
- 하단 내비가 있으면 본문이 가려지지 않도록 하단 여백 확보
- `fullBleed`일 때는 padding 없이 화면 전체 사용

---

### PageHeader

- 타입: component
- 사용 화면: 뒤로가기 또는 제목이 있는 page 화면

**역할**

- 상단 제목, 뒤로가기, 우측 액션을 일관되게 표시한다.

**Props**
| 이름 | 타입 | 설명 |
|---|---|---|
| title | `string` | 화면 제목 |
| onBack | `() => void`? | 뒤로가기 액션 |
| rightAction | `ReactNode?` | 우측 버튼/아이콘 |

**UI**

- 좌측: 뒤로가기 아이콘 버튼
- 중앙 또는 좌측 정렬: 제목
- 우측: 선택 액션 영역

**동작**
| 조건 | 동작 |
|---|---|
| `onBack` 없음 | 뒤로가기 영역 숨김 |
| `rightAction` 없음 | 우측 영역은 레이아웃 균형만 유지 |

---

### BottomNav

- 타입: component
- 사용 화면: `C-NAV-USER`, `C-NAV-MERCHANT` 사용 화면

**역할**

- 사용자/가맹점 권한별 하단 탭 이동 또는 non-route 액션 진입을 제공한다.

**Props**
| 이름 | 타입 | 설명 |
|---|---|---|
| type | `'user' \| 'merchant'` | 내비게이션 타입 |
| active | `string` | 활성 탭 id |
| onNavigate | `(target: string) => void` | route 이동 또는 non-route 액션 요청 |

**UI**

- 화면 하단 고정
- 아이콘과 짧은 라벨 표시
- 사용자 내비게이션은 QR 결제 탭을 중앙 강조
- 비활성 또는 미구현 탭은 disabled 상태로 표시
- QR 결제처럼 별도 route가 없는 탭은 page가 전달한 액션 핸들러로 처리한다.

---

## Feedback

### ConfirmDialog

- 타입: modal component
- 사용 화면: 로그아웃 등 화면 명세에서 확인 모달을 요구하는 액션

**역할**

- 되돌리기 어렵거나 확인이 필요한 액션 전에 사용자 확인을 받는다.

**Props**
| 이름 | 타입 | 설명 |
|---|---|---|
| open | `boolean` | 표시 여부 |
| title | `string` | 제목 |
| description | `string?` | 보조 설명 |
| confirmText | `string` | 확인 버튼 문구 |
| cancelText | `string` | 취소 버튼 문구 |
| variant | `'default' \| 'danger'` | 확인 액션 강조 타입 |
| onConfirm | `() => void` | 확인 |
| onCancel | `() => void` | 취소 |

**UI**

- 부모 화면 위 오버레이
- 제목, 설명, 버튼 2개
- `danger`일 때 확인 버튼을 위험 액션 스타일로 표시

---

### ProcessingState

- 타입: component
- 사용 화면: 가입 처리중, 결제 처리중, 충전 처리중, 환불 처리중, 정산 처리중

**역할**

- 서버 처리 대기 상태를 표시한다.
- 실패 상태는 이 컴포넌트에 렌더링하지 않고, 직전 입력/확인 화면으로 돌아가 실패 사유를 표시한다.

**Props**
| 이름 | 타입 | 설명 |
|---|---|---|
| loadingText | `string` | 처리중 문구 |
| summary | `ReactNode?` | 금액, 가맹점명 등 보조 정보 |

**UI**

- 스피너, 처리중 문구, 보조 정보

---

### ResultState

- 타입: component
- 사용 화면: 완료 화면, 세션 만료, 네트워크 오류, 권한 안내

**역할**

- 성공, 실패, 안내성 최종 상태를 표시한다.

**Props**
| 이름 | 타입 | 설명 |
|---|---|---|
| variant | `'success' \| 'error' \| 'info'` | 상태 타입 |
| title | `string` | 제목 |
| description | `string?` | 설명 |
| details | `ReactNode?` | 승인번호, 일시, 계좌 등 상세 정보 |
| primaryText | `string` | 주 버튼 문구 |
| secondaryText | `string?` | 보조 버튼 문구 |
| onPrimary | `() => void` | 주 액션 |
| onSecondary | `() => void`? | 보조 액션 |

**UI**

- 상태 아이콘
- 제목과 설명
- 상세 정보 영역
- 하단 액션 버튼

---

### EmptyState

- 타입: component
- 사용 화면: 결제 내역, 충전 내역, 환불 내역, 정산 내역

**역할**

- 목록 데이터가 없을 때 안내 문구를 표시한다.

**Props**
| 이름 | 타입 | 설명 |
|---|---|---|
| message | `string` | 빈 상태 문구 |
| action | `ReactNode?` | 선택 액션 |

**UI**

- 중앙 또는 목록 영역 상단에 간결한 안내 표시
- 선택 액션이 있으면 안내 아래 배치

---

## Input

### TextField

- 타입: component
- 사용 화면: 로그인, 회원가입, 계좌 입력, 사업자 정보 입력

**역할**

- 단일 텍스트 입력을 일관된 스타일로 표시한다.

**Props**
| 이름 | 타입 | 설명 |
|---|---|---|
| label | `string` | 라벨 |
| value | `string` | 입력값 |
| onChange | `(value: string) => void` | 값 변경 |
| type | `string?` | input type |
| placeholder | `string?` | placeholder |
| error | `string?` | 오류 메시지 |
| disabled | `boolean?` | 비활성화 |

**UI**

- 라벨, 입력 필드, 오류 메시지
- 오류가 있으면 입력 테두리와 메시지 강조

---

### SelectField

- 타입: component
- 사용 화면: 통신사 선택, 은행 선택

**역할**

- 제한된 선택지 중 하나를 선택한다.

**Props**
| 이름 | 타입 | 설명 |
|---|---|---|
| label | `string` | 라벨 |
| value | `string` | 선택값 |
| options | `{ label: string; value: string }[]` | 선택지 |
| onChange | `(value: string) => void` | 선택 변경 |
| placeholder | `string?` | 미선택 문구 |
| error | `string?` | 오류 메시지 |

**UI**

- 모바일에서 탭하기 쉬운 높이
- 선택 목록은 바텀시트 또는 모바일 친화적 팝오버로 표시

---

### CheckboxGroup

- 타입: component
- 사용 화면: 사용자/가맹점 약관 동의

**역할**

- 전체 동의와 개별 동의를 관리한다.

**Props**
| 이름 | 타입 | 설명 |
|---|---|---|
| items | `{ id: string; label: string; required: boolean }[]` | 약관 목록 |
| checkedIds | `string[]` | 체크된 항목 |
| onChange | `(checkedIds: string[]) => void` | 체크 변경 |

**UI**

- 전체 동의 체크박스
- 필수/선택 약관 목록
- 필수 여부 배지 또는 텍스트 표시

**동작**
| 조건 | 동작 |
|---|---|
| 전체 동의 체크 | 모든 항목 체크 |
| 전체 동의 해제 | 모든 항목 해제 |
| 개별 항목 변경 | 전체 동의 상태 자동 갱신 |

---

### AmountInput

- 타입: component
- 사용 화면: U-PAY-02, U-CHG-01, U-REF-01, M-SET-03

**역할**

- 원화 금액 입력과 표시를 담당한다.

**Props**
| 이름 | 타입 | 설명 |
|---|---|---|
| value | `number` | 원 단위 금액 |
| onChange | `(value: number) => void` | 값 변경 |
| placeholder | `string?` | 미입력 문구 |
| max | `number?` | 최대 입력 가능 금액 |
| error | `string?` | 오류 메시지 |
| disabled | `boolean?` | 비활성화 |

**UI**

- 대형 금액 표시
- 입력값은 `10,000원` 형식
- 오류 메시지는 하단에 표시
- 금액은 우측 정렬

**동작**
| 조건 | 동작 |
|---|---|
| 숫자 입력 | 금액 갱신 |
| 삭제 | 마지막 자리 삭제 |
| 전체 삭제 | 0 또는 빈 상태로 변경 |
| 최대값 초과 | 화면별 정책에 따라 차단하거나 오류 표시 |

---

### NumberPad

- 타입: component
- 사용 화면: 금액 입력, 인증 코드 입력

**역할**

- 모바일 숫자 입력 키패드를 제공한다.

**Props**
| 이름 | 타입 | 설명 |
|---|---|---|
| onDigit | `(digit: string) => void` | 숫자 입력 |
| onBackspace | `() => void` | 한 자리 삭제 |
| onClear | `() => void`? | 전체 삭제 |
| disabled | `boolean?` | 비활성화 |

**UI**

- 0-9 숫자 버튼
- 삭제 아이콘 버튼
- 필요한 경우 전체 삭제 버튼
- 버튼 크기는 엄지 터치에 적합해야 한다.

---

### PinCodeInput

- 타입: component
- 사용 화면: SMS 인증번호, 계좌 1원 인증 코드

**역할**

- 고정 길이 숫자 코드를 입력하고 표시한다.

**Props**
| 이름 | 타입 | 설명 |
|---|---|---|
| value | `string` | 입력 코드 |
| length | `number` | 코드 길이 |
| onChange | `(value: string) => void` | 값 변경 |
| error | `string?` | 오류 메시지 |
| disabled | `boolean?` | 비활성화 |

**UI**

- 자리수별 입력 칸 또는 단일 입력 필드
- 숫자만 입력
- 오류 메시지 하단 표시

---

### SecurePinInput

- 타입: component
- 사용 화면: 회원가입 PIN 설정/확인, 결제 PIN, 충전 PIN, 환불 PIN, 가맹점 결제 취소 PIN, 가맹점 정산 PIN

**역할**

- 민감 작업 전에 고정 길이 숫자 PIN을 입력하고 마스킹 표시한다.

**Props**
| 이름 | 타입 | 설명 |
|---|---|---|
| value | `string` | 입력 PIN |
| length | `number` | PIN 길이 |
| onChange | `(value: string) => void` | 값 변경 |
| onComplete | `(value: string) => void`? | 길이 충족 시 호출 |
| error | `string?` | 오류 메시지 |
| disabled | `boolean?` | 비활성화 |

**UI**

- 입력값은 숫자만 허용한다.
- PIN 숫자는 화면에 그대로 노출하지 않고 마스킹 표시한다.
- 숫자패드는 `NumberPad`를 재사용하거나 같은 동작 규칙을 따른다.

---

## Data Display

### BalanceCard

- 타입: component
- 사용 화면: 사용자 홈, 금액 입력 화면

**역할**

- 현재 잔액과 새로고침 액션을 표시한다.

**Props**
| 이름 | 타입 | 설명 |
|---|---|---|
| balance | `number` | 잔액 |
| label | `string?` | 라벨 |
| onRefresh | `() => void`? | 새로고침 |
| refreshing | `boolean?` | 새로고침 중 |

**UI**

- 라벨
- `10,000원` 형식의 잔액
- 선택 새로고침 아이콘 버튼

---

### SummaryCard

- 타입: component
- 사용 화면: 상세 화면, 처리 결과, 완료 화면 등 요약 정보가 필요한 화면

**역할**

- 확인 화면의 label/value 요약 정보를 표시한다.

**Props**
| 이름 | 타입 | 설명 |
|---|---|---|
| rows | `{ label: string; value: ReactNode; emphasis?: boolean }[]` | 요약 행 |
| title | `string?` | 카드 제목 |

**UI**

- 행 단위 label/value
- 금액 값은 우측 정렬
- `emphasis` 행은 굵게 또는 강조색으로 표시

---

### ListItem

- 타입: component
- 사용 화면: 내역 조회, 최근 결제내역, 정산내역, 메뉴 목록

**역할**

- 반복 목록의 한 행을 표시한다.

**Props**
| 이름 | 타입 | 설명 |
|---|---|---|
| title | `string` | 주요 텍스트 |
| description | `string?` | 보조 텍스트 |
| amount | `number?` | 금액 |
| badge | `ReactNode?` | 상태 배지 |
| rightAction | `ReactNode?` | 우측 액션 |
| onClick | `() => void`? | 행 탭 |
| disabled | `boolean?` | 비활성화 |

**UI**

- 좌측 주요 정보
- 우측 금액 또는 액션
- 탭 가능한 경우 시각적 피드백 제공

---

### StatusBadge

- 타입: component
- 사용 화면: 내역 목록, 상세 화면, 환불 가능 여부

**역할**

- 상태를 짧은 텍스트와 색상으로 표시한다.

**Props**
| 이름 | 타입 | 설명 |
|---|---|---|
| children | `ReactNode` | 배지 텍스트 |
| variant | `'success' \| 'warning' \| 'danger' \| 'neutral'` | 상태 타입 |

**UI**

- 짧은 라벨
- 상태별 색상
- 작은 화면에서 줄바꿈되지 않도록 표시

---

### AccountRow

- 타입: component
- 사용 화면: 계좌 관리, 계좌 정보 표시

**역할**

- 은행명, 마스킹 계좌번호, 선택 상태, 계좌 액션을 표시한다.

**Props**
| 이름 | 타입 | 설명 |
|---|---|---|
| bankName | `string` | 은행명 |
| maskedAccountNumber | `string` | 마스킹 계좌번호 |
| holderName | `string?` | 예금주명 |
| primary | `boolean?` | 주거래 계좌 여부 |
| selected | `boolean?` | 선택 여부 |
| mode | `'select' \| 'manage'` | 선택/관리 모드 |
| onSelect | `() => void`? | 계좌 선택 |
| onSetPrimary | `() => void`? | 주거래 변경 |
| onDelete | `() => void`? | 삭제 |

**UI**

- 은행명과 계좌번호
- 주거래 배지는 화면에서 필요한 경우에만 표시
- 선택 모드에서는 라디오 표시
- 관리 모드 액션은 page가 전달한 핸들러가 있을 때만 표시
