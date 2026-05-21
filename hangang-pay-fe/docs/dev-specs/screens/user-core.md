# 사용자 핵심 플로우 화면 명세

대상 화면: `U-01`, `U-PAY-*`, `U-CHG-*`, `U-REF-*`

## 공통 규칙

- 금액 입력 화면은 숫자패드 기반으로 구현한다.
- 금액 표시 형식은 전 화면 공통으로 `10,000원` 형식을 사용한다.

---

## 사용자 홈

### U-01 사용자 홈

- type: page
- route: `/home`
- auth: user
- purpose: 사용자 잔액과 핵심 결제/충전/환불 플로우 진입점을 제공한다.

**actions**

| trigger | result |
| --- | --- |
| QR 결제 | 카메라 QR 처리 시작 |
| QR 인식 성공 | `/pay/amount` |
| QR 인식 실패 또는 취소 | 현재 화면 유지 |
| 충전 | `/charge/amount` |
| 환불 | `/refund/check` |

**implementation notes**

- QR 인식은 별도 route 화면으로 만들지 않는다.
- 모바일 웹 카메라 권한은 브라우저 UX를 따른다.
- QR payload에서 가맹점 식별자를 얻은 뒤 결제 금액 입력 화면으로 이동한다.

---

## 결제 플로우

순서: QR 결제 -> 카메라 QR 처리 -> 가맹점 확인 및 금액 입력 -> PIN 입력 -> 처리중 -> 완료

### U-PAY-01 QR 카메라 처리

- type: non-route flow
- route: 없음
- auth: user
- purpose: 카메라로 가맹점 QR을 인식하고 결제 플로우에 필요한 가맹점 식별자를 확보한다.

**states**

| state | contract |
| --- | --- |
| requesting_permission | 카메라 권한 요청중 |
| scanning | QR 인식 대기 |
| error | 권한 거부, 카메라 사용 불가, QR 인식 실패 사유 표시 |

**actions**

| trigger | condition | result |
| --- | --- | --- |
| QR 인식 성공 | 가맹점 식별자 확인 | `/pay/amount` |
| 취소 | - | `/home` |
| 재시도 | error 상태 | 카메라 QR 처리 재시작 |

**implementation notes**

- 구현 방식은 브라우저 지원 범위를 확인해 선택한다.
- 1순위: `navigator.mediaDevices.getUserMedia()`로 카메라 스트림을 열고 `BarcodeDetector`로 QR을 인식한다.
- fallback: `BarcodeDetector` 미지원 브라우저는 `@zxing/browser` 또는 `html5-qrcode` 같은 웹 QR 라이브러리를 사용한다.
- 단순 파일 업로드 방식은 실시간 결제 UX가 아니므로 기본 방식으로 사용하지 않는다.

---

### U-PAY-02 결제 금액 입력

- type: page
- route: `/pay/amount`
- auth: user
- purpose: QR에서 확인한 가맹점을 보여주고 결제 금액을 입력한다.

**states**

| state | contract |
| --- | --- |
| loading | 가맹점 정보 확인중 |
| ready | 금액 입력 가능 |
| error | 가맹점 확인 실패 또는 금액 검증 실패 사유 표시 |

**actions**

| trigger | condition | result |
| --- | --- | --- |
| 다음 | 유효한 금액 | `/pay/pin` |
| 다음 | 검증 실패 | 현재 화면 `error` 상태 |
| 취소 | - | `/home` |

---

### U-PAY-03 결제 PIN 입력

- type: page
- route: `/pay/pin`
- auth: user
- purpose: 결제 실행 전 사용자 PIN을 확인한다.

**actions**

| trigger | condition | result |
| --- | --- | --- |
| PIN 입력 완료 | PIN 형식 유효 | `/pay/processing` |
| PIN 입력 완료 | 검증 실패 | 현재 화면에서 오류 표시 후 재입력 |
| 이전 | - | `/pay/amount` |

---

### U-PAY-04 결제 처리중

- type: page
- route: `/pay/processing`
- auth: user
- purpose: 결제 요청을 처리하고 결과 화면으로 전환한다.
- initial state: loading

**states**

| state | contract |
| --- | --- |
| loading | 결제 처리중 |

**actions**

| trigger | condition | result |
| --- | --- | --- |
| 처리 성공 | - | `/pay/complete` |
| 처리 실패 | - | `/pay/amount`로 돌아가 실패 사유 표시 |

---

### U-PAY-05 결제 완료

- type: page
- route: `/pay/complete`
- auth: user
- purpose: 결제 완료 결과를 안내하고 홈으로 복귀한다.

**actions**

| trigger | result |
| --- | --- |
| 홈으로 | `/home` |

---

## 충전 플로우

순서: 충전 금액 입력 -> PIN 입력 -> 처리중 -> 완료

### U-CHG-01 충전 금액 입력

- type: page
- route: `/charge/amount`
- auth: user
- purpose: 숫자패드로 충전 금액을 입력한다.

**states**

| state | contract |
| --- | --- |
| ready | 금액 입력 가능 |
| error | 충전 금액 검증 실패 사유 표시 |

**actions**

| trigger | condition | result |
| --- | --- | --- |
| 다음 | 유효한 금액 | `/charge/pin` |
| 다음 | 검증 실패 | 현재 화면 `error` 상태 |
| 이전 | - | `/home` |

---

### U-CHG-02 충전 PIN 입력

- type: page
- route: `/charge/pin`
- auth: user
- purpose: 충전 실행 전 사용자 PIN을 확인한다.

**actions**

| trigger | condition | result |
| --- | --- | --- |
| PIN 입력 완료 | PIN 형식 유효 | `/charge/processing` |
| PIN 입력 완료 | 검증 실패 | 현재 화면에서 오류 표시 후 재입력 |
| 이전 | - | `/charge/amount` |

---

### U-CHG-03 충전 처리중

- type: page
- route: `/charge/processing`
- auth: user
- purpose: 충전 요청을 처리하고 결과 화면으로 전환한다.
- initial state: loading

**states**

| state | contract |
| --- | --- |
| loading | 충전 처리중 |

**actions**

| trigger | condition | result |
| --- | --- | --- |
| 처리 성공 | - | `/charge/complete` |
| 처리 실패 | - | `/charge/amount`로 돌아가 실패 사유 표시 |

---

### U-CHG-04 충전 완료

- type: page
- route: `/charge/complete`
- auth: user
- purpose: 충전 완료 결과를 안내하고 홈으로 복귀한다.

**actions**

| trigger | result |
| --- | --- |
| 홈으로 | `/home` |

---

## 환불 플로우

순서: 환불 가능 여부 및 금액 입력 -> PIN 입력 -> 처리중 -> 완료

### U-REF-01 환불 가능 여부 및 금액 입력

- type: page
- route: `/refund/check`
- auth: user
- purpose: 환불 가능 여부를 확인하고 숫자패드로 환불 금액을 입력한다.

**states**

| state | contract |
| --- | --- |
| loading | 환불 가능 여부 확인중 |
| available | 환불 금액 입력 가능 |
| unavailable | 환불 불가 사유 표시 |
| error | 환불 가능 여부 확인 또는 금액 검증 실패 사유 표시 |

**actions**

| trigger | condition | result |
| --- | --- | --- |
| 다음 | 환불 가능 및 유효한 금액 | `/refund/pin` |
| 다음 | 검증 실패 | 현재 화면 `error` 상태 |
| 확인 | 환불 불가 상태 | `/home` |
| 이전 | - | `/home` |

---

### U-REF-02 환불 PIN 입력

- type: page
- route: `/refund/pin`
- auth: user
- purpose: 환불 실행 전 사용자 PIN을 확인한다.

**actions**

| trigger | condition | result |
| --- | --- | --- |
| PIN 입력 완료 | PIN 형식 유효 | `/refund/processing` |
| PIN 입력 완료 | 검증 실패 | 현재 화면에서 오류 표시 후 재입력 |
| 이전 | - | `/refund/check` |

---

### U-REF-03 환불 처리중

- type: page
- route: `/refund/processing`
- auth: user
- purpose: 환불 요청을 처리하고 결과 화면으로 전환한다.
- initial state: loading

**states**

| state | contract |
| --- | --- |
| loading | 환불 처리중 |

**actions**

| trigger | condition | result |
| --- | --- | --- |
| 처리 성공 | - | `/refund/complete` |
| 처리 실패 | - | `/refund/check`로 돌아가 실패 사유 표시 |

---

### U-REF-04 환불 완료

- type: page
- route: `/refund/complete`
- auth: user
- purpose: 환불 완료 결과를 안내하고 홈으로 복귀한다.

**actions**

| trigger | result |
| --- | --- |
| 홈으로 | `/home` |
