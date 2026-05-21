# 사용자 마이페이지 / 내역 / 계좌 화면 명세

대상 화면: `U-MY-*`, `U-ACC-*`

---

## 마이페이지

### U-MY-01 마이페이지

- type: page
- route: `/mypage`
- auth: user
- purpose: 사용자 내역, 계좌 관리, 로그아웃 진입점을 제공한다.

**actions**

| trigger | result |
| --- | --- |
| 내역 확인 | `/mypage/history` |
| 계좌 관리 | `/mypage/accounts` |
| 로그아웃 | `C-LOGOUT-01` 모달 열기 |

---

## 내역 조회 플로우

순서: 내역 조회 -> 전체/결제/충전/환불 탭 선택 -> 항목 선택 -> 유형별 상세

### U-MY-HIST-01 내역 조회

- type: page
- route: `/mypage/history`
- auth: user
- purpose: 사용자 거래 내역을 전체, 결제, 충전, 환불 탭으로 조회한다.

**states**

| state | contract |
| --- | --- |
| loading | 내역 조회중 |
| ready | 선택한 탭의 내역 표시 |
| empty | 선택한 탭의 내역 없음 |
| error | 내역 조회 실패 사유 표시 |

**actions**

| trigger | condition | result |
| --- | --- | --- |
| 전체 탭 | - | 현재 화면에서 전체 내역 표시 |
| 결제 탭 | - | 현재 화면에서 결제 내역 표시 |
| 충전 탭 | - | 현재 화면에서 충전 내역 표시 |
| 환불 탭 | - | 현재 화면에서 환불 내역 표시 |
| 결제 row 클릭 | - | `/mypage/history/payments/:id` |
| 충전 row 클릭 | - | `/mypage/history/charges/:id` |
| 환불 row 클릭 | - | `/mypage/history/refunds/:id` |
| 뒤로 | - | `/mypage` |

---

### U-MY-HIST-PAY-01 결제 상세

- type: page
- route: `/mypage/history/payments/:id`
- auth: user
- purpose: 선택한 결제 내역의 상세 정보를 조회한다.

**states**

| state | contract |
| --- | --- |
| loading | 상세 조회중 |
| ready | 결제 상세 표시 |
| error | 상세 조회 실패 사유 표시 |

**actions**

| trigger | result |
| --- | --- |
| 뒤로 | `/mypage/history` |

---

### U-MY-HIST-CHG-01 충전 상세

- type: page
- route: `/mypage/history/charges/:id`
- auth: user
- purpose: 선택한 충전 내역의 상세 정보를 조회한다.

**states**

| state | contract |
| --- | --- |
| loading | 상세 조회중 |
| ready | 충전 상세 표시 |
| error | 상세 조회 실패 사유 표시 |

**actions**

| trigger | result |
| --- | --- |
| 뒤로 | `/mypage/history` |

---

### U-MY-HIST-REF-01 환불 상세

- type: page
- route: `/mypage/history/refunds/:id`
- auth: user
- purpose: 선택한 환불 내역의 상세 정보를 조회한다.

**states**

| state | contract |
| --- | --- |
| loading | 상세 조회중 |
| ready | 환불 상세 표시 |
| error | 상세 조회 실패 사유 표시 |

**actions**

| trigger | result |
| --- | --- |
| 뒤로 | `/mypage/history` |

---

## 계좌 관리 플로우

순서: 계좌 관리 -> 계좌 추가 및 1원 인증 -> 계좌 추가 완료

### U-ACC-01 계좌 관리

- type: page
- route: `/mypage/accounts`
- auth: user
- purpose: 등록된 사용자 계좌를 조회하고 계좌 추가 플로우로 진입한다.

**states**

| state | contract |
| --- | --- |
| loading | 계좌 목록 조회중 |
| ready | 계좌 목록 표시 |
| empty | 등록된 계좌 없음 |
| error | 계좌 목록 조회 실패 사유 표시 |

**actions**

| trigger | result |
| --- | --- |
| 계좌 추가 | `/mypage/accounts/add` |
| 뒤로 | `/mypage` |

---

### U-ACC-02 계좌 추가 및 1원 인증

- type: page
- route: `/mypage/accounts/add`
- auth: user
- purpose: 계좌 정보를 입력하고 같은 화면에서 1원 인증을 완료한다.

**states**

| state | contract |
| --- | --- |
| idle | 계좌 입력 대기 |
| code_sent | 1원 인증번호 입력 및 타이머 진행 |
| verified | 계좌 인증 완료 |
| error | 계좌 확인 또는 인증 실패 사유 표시 |

**actions**

| trigger | condition | result |
| --- | --- | --- |
| 1원 인증 발송 | 발송 성공 | 현재 화면 `code_sent` 상태 |
| 인증 확인 | 인증 성공 | 현재 화면 `verified` 상태 |
| 인증 확인 | 인증 실패 | 현재 화면 `error` 상태 |
| 재발송 | - | 현재 화면 `code_sent` 상태로 타이머 초기화 |
| 다음 | 계좌 인증 완료 | `/mypage/accounts/complete` |
| 이전 | - | `/mypage/accounts` |

---

### U-ACC-03 계좌 추가 완료

- type: page
- route: `/mypage/accounts/complete`
- auth: user
- purpose: 계좌 추가 완료를 안내하고 계좌 관리 화면으로 복귀한다.

**actions**

| trigger | result |
| --- | --- |
| 확인 | `/mypage/accounts` |
