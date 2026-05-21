# 가맹점 화면 명세

대상 화면: `M-*`

---

## 가맹점 홈

### M-01 가맹점 홈

- type: page
- route: `/merchant/home`
- auth: merchant
- purpose: 가맹점 주요 기능 진입점과 최근 결제내역을 제공한다.

**states**

| state | contract |
| --- | --- |
| loading | 홈 데이터 조회중 |
| ready | 홈 데이터와 최근 결제내역 표시 |
| empty | 최근 결제내역 없음 |
| error | 홈 데이터 조회 실패 사유 표시 |

**actions**

| trigger | result |
| --- | --- |
| QR 조회 | `/merchant/qr` |
| 결제내역 | `/merchant/payments` |
| 정산내역 | `/merchant/settlements` |
| 정산신청 | `/merchant/settlements/amount` |
| 최근 결제 row 클릭 | `/merchant/payments/:id` |

---

## QR 조회

### M-QR-01 QR 조회

- type: page
- route: `/merchant/qr`
- auth: merchant
- purpose: 사용자 결제에 사용할 가맹점 QR을 조회한다.

**states**

| state | contract |
| --- | --- |
| loading | QR 조회중 |
| ready | QR 표시 |
| error | QR 조회 실패 사유 표시 |

**actions**

| trigger | result |
| --- | --- |
| 새로고침 | 현재 화면에서 QR 재조회 |
| 뒤로 | `/merchant/home` |

---

## 결제내역 / 취소 플로우

순서: 결제내역 -> 결제상세 -> 취소 버튼 -> PIN 입력 -> 처리중 -> 완료

### M-PAY-01 결제내역

- type: page
- route: `/merchant/payments`
- auth: merchant
- purpose: 가맹점 결제내역을 조회한다.

**states**

| state | contract |
| --- | --- |
| loading | 결제내역 조회중 |
| ready | 결제내역 표시 |
| empty | 결제내역 없음 |
| error | 결제내역 조회 실패 사유 표시 |

**actions**

| trigger | result |
| --- | --- |
| 결제 row 클릭 | `/merchant/payments/:id` |
| 뒤로 | `/merchant/home` |

---

### M-PAY-02 결제 상세

- type: page
- route: `/merchant/payments/:id`
- auth: merchant
- purpose: 선택한 결제의 상세 정보를 조회하고 취소 플로우로 진입한다.

**states**

| state | contract |
| --- | --- |
| loading | 결제 상세 조회중 |
| ready | 결제 상세 표시 |
| error | 결제 상세 조회 또는 취소 실패 사유 표시 |

**actions**

| trigger | condition | result |
| --- | --- | --- |
| 취소 | 취소 가능 | `/merchant/payments/:id/cancel/pin` |
| 취소 | 취소 불가 | 현재 화면에서 취소 불가 사유 표시 |
| 뒤로 | - | `/merchant/payments` |

---

### M-PAY-03 결제 취소 PIN 입력

- type: page
- route: `/merchant/payments/:id/cancel/pin`
- auth: merchant
- purpose: 결제 취소 실행 전 가맹점 PIN을 확인한다.

**actions**

| trigger | condition | result |
| --- | --- | --- |
| PIN 입력 완료 | PIN 형식 유효 | `/merchant/payments/:id/cancel/processing` |
| PIN 입력 완료 | 검증 실패 | 현재 화면에서 오류 표시 후 재입력 |
| 이전 | - | `/merchant/payments/:id` |

---

### M-PAY-04 결제 취소 처리중

- type: page
- route: `/merchant/payments/:id/cancel/processing`
- auth: merchant
- purpose: 결제 취소 요청을 처리하고 결과 화면으로 전환한다.
- initial state: loading

**states**

| state | contract |
| --- | --- |
| loading | 결제 취소 처리중 |

**actions**

| trigger | condition | result |
| --- | --- | --- |
| 처리 성공 | - | `/merchant/payments/:id/cancel/complete` |
| 처리 실패 | - | `/merchant/payments/:id`로 돌아가 실패 사유 표시 |

---

### M-PAY-05 결제 취소 완료

- type: page
- route: `/merchant/payments/:id/cancel/complete`
- auth: merchant
- purpose: 결제 취소 완료 결과를 안내한다.

**actions**

| trigger | result |
| --- | --- |
| 확인 | `/merchant/payments/:id` |
| 홈으로 | `/merchant/home` |

---

## 정산내역

### M-SET-01 정산내역

- type: page
- route: `/merchant/settlements`
- auth: merchant
- purpose: 가맹점 정산내역을 조회한다.

**states**

| state | contract |
| --- | --- |
| loading | 정산내역 조회중 |
| ready | 정산내역 표시 |
| empty | 정산내역 없음 |
| error | 정산내역 조회 실패 사유 표시 |

**actions**

| trigger | result |
| --- | --- |
| 정산 row 클릭 | `/merchant/settlements/:id` |
| 정산신청 | `/merchant/settlements/amount` |
| 뒤로 | `/merchant/home` |

---

### M-SET-02 정산 상세

- type: page
- route: `/merchant/settlements/:id`
- auth: merchant
- purpose: 선택한 정산내역의 상세 정보를 조회한다.

**states**

| state | contract |
| --- | --- |
| loading | 정산 상세 조회중 |
| ready | 정산 상세 표시 |
| error | 정산 상세 조회 실패 사유 표시 |

**actions**

| trigger | result |
| --- | --- |
| 뒤로 | `/merchant/settlements` |

---

## 정산신청 플로우

순서: 정산금액 확인 -> PIN 입력 -> 처리중 -> 완료

### M-SET-03 정산금액 확인

- type: page
- route: `/merchant/settlements/amount`
- auth: merchant
- purpose: 정산 가능 금액을 확인하고 정산 신청을 시작한다.

**states**

| state | contract |
| --- | --- |
| loading | 정산 가능 금액 조회중 |
| ready | 정산 신청 가능 |
| error | 정산 가능 금액 조회 또는 신청 실패 사유 표시 |

**actions**

| trigger | condition | result |
| --- | --- | --- |
| 신청 | 정산 신청 가능 | `/merchant/settlements/pin` |
| 신청 | 검증 실패 | 현재 화면 `error` 상태 |
| 이전 | - | `/merchant/home` 또는 `/merchant/settlements` |

---

### M-SET-04 정산 PIN 입력

- type: page
- route: `/merchant/settlements/pin`
- auth: merchant
- purpose: 정산 신청 실행 전 가맹점 PIN을 확인한다.

**actions**

| trigger | condition | result |
| --- | --- | --- |
| PIN 입력 완료 | PIN 형식 유효 | `/merchant/settlements/processing` |
| PIN 입력 완료 | 검증 실패 | 현재 화면에서 오류 표시 후 재입력 |
| 이전 | - | `/merchant/settlements/amount` |

---

### M-SET-05 정산 처리중

- type: page
- route: `/merchant/settlements/processing`
- auth: merchant
- purpose: 정산 신청 요청을 처리하고 결과 화면으로 전환한다.
- initial state: loading

**states**

| state | contract |
| --- | --- |
| loading | 정산 처리중 |

**actions**

| trigger | condition | result |
| --- | --- | --- |
| 처리 성공 | - | `/merchant/settlements/complete` |
| 처리 실패 | - | `/merchant/settlements/amount`로 돌아가 실패 사유 표시 |

---

### M-SET-06 정산 신청 완료

- type: page
- route: `/merchant/settlements/complete`
- auth: merchant
- purpose: 정산 신청 완료 결과를 안내한다.

**actions**

| trigger | result |
| --- | --- |
| 확인 | `/merchant/settlements` |
| 홈으로 | `/merchant/home` |

---

## 마이페이지

### M-MY-01 가맹점 마이페이지

- type: page
- route: `/merchant/mypage`
- auth: merchant
- purpose: 가맹점 정보와 로그아웃 진입점을 제공한다.

**actions**

| trigger | result |
| --- | --- |
| 가맹점 정보 | `/merchant/mypage/info` |
| 로그아웃 | `C-LOGOUT-01` 모달 열기 |

---

### M-MY-02 가맹점 정보

- type: page
- route: `/merchant/mypage/info`
- auth: merchant
- purpose: 가맹점 등록 정보를 조회한다.

**states**

| state | contract |
| --- | --- |
| loading | 가맹점 정보 조회중 |
| ready | 가맹점 정보 표시 |
| error | 가맹점 정보 조회 실패 사유 표시 |

**actions**

| trigger | result |
| --- | --- |
| 뒤로 | `/merchant/mypage` |
