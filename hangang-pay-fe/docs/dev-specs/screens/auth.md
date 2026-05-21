# Auth / Signup 화면 명세

대상 화면: `A-*`, `U-REG-*`, `M-REG-*`

---

## Auth 플로우

### A-01 시작 화면

- type: page
- route: `/`
- auth: public
- purpose: 로그인 또는 회원가입 플로우로 진입한다.

**actions**

| trigger | result |
| --- | --- |
| 로그인 | `/login` |
| 사용자 회원가입 | `/register/terms` |
| 가맹점 회원가입 | `/merchant/register/terms` |

---

### A-02 로그인 화면

- type: page
- route: `/login`
- auth: public
- purpose: 사용자 또는 가맹점 계정으로 로그인한다.

**states**

| state | contract |
| --- | --- |
| idle | 로그인 입력 대기 |
| submitting | 로그인 요청 처리중 |
| error | 로그인 실패 사유 표시 |

**actions**

| trigger | condition | result |
| --- | --- | --- |
| 로그인 | 사용자 로그인 성공 | `/home` |
| 로그인 | 가맹점 로그인 성공 | `/merchant/home` |
| 로그인 | 실패 | 현재 화면 `error` 상태 |
| 회원가입 | - | `/` |

---

## 사용자 회원가입 플로우

순서: 약관 동의 -> 휴대폰 본인인증 -> 비밀번호 설정 -> 계좌 입력 및 1원 인증 -> PIN 입력 -> PIN 재입력 -> 회원가입 처리중 -> 가입 완료

### U-REG-01 사용자 가입: 약관 동의

- type: page
- route: `/register/terms`
- auth: public
- purpose: 사용자 회원가입 약관 동의를 수집한다.

**actions**

| trigger | result |
| --- | --- |
| 다음 | `/register/verify` |
| 이전 | `/` |

---

### U-REG-02 사용자 가입: 휴대폰 본인인증

- type: page
- route: `/register/verify`
- auth: public
- purpose: 사용자 본인 명의의 휴대폰 인증을 완료한다.

**states**

| state | contract |
| --- | --- |
| idle | 인증번호 발송 전 |
| code_sent | 인증번호 입력 및 타이머 진행 |
| error | 인증 실패 사유 표시 |

**actions**

| trigger | condition | result |
| --- | --- | --- |
| 인증번호 발송 | - | 현재 화면 `code_sent` 상태 |
| 확인 | 인증 성공 | `/register/password` |
| 확인 | 인증 실패 | 현재 화면 `error` 상태 |
| 재발송 | - | 현재 화면 `code_sent` 상태로 타이머 초기화 |
| 이전 | - | `/register/terms` |

---

### U-REG-03 사용자 가입: 비밀번호 설정

- type: page
- route: `/register/password`
- auth: public
- purpose: 로그인에 사용할 비밀번호를 설정한다.

**actions**

| trigger | condition | result |
| --- | --- | --- |
| 다음 | 비밀번호와 확인값 일치 | `/register/account` |
| 다음 | 검증 실패 | 현재 화면에서 오류 표시 |
| 이전 | - | `/register/verify` |

---

### U-REG-04 사용자 가입: 계좌 입력 및 1원 인증

- type: page
- route: `/register/account`
- auth: public
- purpose: 충전/환불에 사용할 계좌를 입력하고 같은 화면에서 1원 인증을 완료한다.

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
| 다음 | 계좌 인증 완료 | `/register/pin` |
| 이전 | - | `/register/password` |

---

### U-REG-05 사용자 가입: PIN 입력

- type: page
- route: `/register/pin`
- auth: public
- purpose: 결제 및 민감 작업에 사용할 PIN을 입력한다.

**actions**

| trigger | result |
| --- | --- |
| PIN 입력 완료 | `/register/pin-confirm` |
| 이전 | `/register/account` |

---

### U-REG-06 사용자 가입: PIN 재입력

- type: page
- route: `/register/pin-confirm`
- auth: public
- purpose: PIN을 재입력해 일치 여부를 확인한다.

**actions**

| trigger | condition | result |
| --- | --- | --- |
| PIN 재입력 완료 | 최초 PIN과 일치 | `/register/processing` |
| PIN 재입력 완료 | 불일치 | 현재 화면에서 오류 표시 후 재입력 |
| 이전 | - | `/register/pin` |

---

### U-REG-07 사용자 가입: 회원가입 처리중

- type: page
- route: `/register/processing`
- auth: public
- purpose: 사용자 회원가입 요청을 최종 처리한다.
- initial state: loading

**states**

| state | contract |
| --- | --- |
| loading | 회원가입 처리중 |

**actions**

| trigger | condition | result |
| --- | --- | --- |
| 처리 성공 | - | `/register/complete` |
| 처리 실패 | - | `/register/account`로 돌아가 실패 사유 표시 |

---

### U-REG-08 사용자 가입 완료

- type: page
- route: `/register/complete`
- auth: public
- purpose: 사용자 회원가입 완료를 안내하고 사용자 홈으로 진입한다.

**actions**

| trigger | result |
| --- | --- |
| 시작하기 | `/home` |

---

## 가맹점 회원가입 플로우

순서: 약관 동의 -> 휴대폰 본인인증 -> 비밀번호 설정 -> 사업자 정보 입력 -> 계좌 입력 및 1원 인증 -> PIN 입력 -> PIN 재입력 -> 회원가입 처리중 -> 가입 완료

### M-REG-01 가맹점 가입: 약관 동의

- type: page
- route: `/merchant/register/terms`
- auth: public
- purpose: 가맹점 회원가입 약관 동의를 수집한다.

**actions**

| trigger | result |
| --- | --- |
| 다음 | `/merchant/register/verify` |
| 이전 | `/` |

---

### M-REG-02 가맹점 가입: 휴대폰 본인인증

- type: page
- route: `/merchant/register/verify`
- auth: public
- purpose: 가맹점 대표자 휴대폰 인증을 완료한다.

**states**

| state | contract |
| --- | --- |
| idle | 인증번호 발송 전 |
| code_sent | 인증번호 입력 및 타이머 진행 |
| error | 인증 실패 사유 표시 |

**actions**

| trigger | condition | result |
| --- | --- | --- |
| 인증번호 발송 | - | 현재 화면 `code_sent` 상태 |
| 확인 | 인증 성공 | `/merchant/register/password` |
| 확인 | 인증 실패 | 현재 화면 `error` 상태 |
| 재발송 | - | 현재 화면 `code_sent` 상태로 타이머 초기화 |
| 이전 | - | `/merchant/register/terms` |

---

### M-REG-03 가맹점 가입: 비밀번호 설정

- type: page
- route: `/merchant/register/password`
- auth: public
- purpose: 가맹점 로그인에 사용할 비밀번호를 설정한다.

**actions**

| trigger | condition | result |
| --- | --- | --- |
| 다음 | 비밀번호와 확인값 일치 | `/merchant/register/business` |
| 다음 | 검증 실패 | 현재 화면에서 오류 표시 |
| 이전 | - | `/merchant/register/verify` |

---

### M-REG-04 가맹점 가입: 사업자 정보 입력

- type: page
- route: `/merchant/register/business`
- auth: public
- purpose: 사업자 정보를 입력하고 백엔드 사업자 확인 API로 유효성을 확인한다.

**states**

| state | contract |
| --- | --- |
| idle | 사업자 확인 전 |
| checking | 사업자 확인 요청 처리중 |
| verified | 사업자 확인 완료 |
| error | 사업자 확인 실패 사유 표시 |

**actions**

| trigger | condition | result |
| --- | --- | --- |
| 사업자 확인 | 요청 성공 | 현재 화면 `verified` 상태 |
| 사업자 확인 | 요청 실패 | 현재 화면 `error` 상태 |
| 다음 | 사업자 확인 완료 | `/merchant/register/account` |
| 이전 | - | `/merchant/register/password` |

---

### M-REG-05 가맹점 가입: 계좌 입력 및 1원 인증

- type: page
- route: `/merchant/register/account`
- auth: public
- purpose: 정산 계좌를 입력하고 같은 화면에서 1원 인증을 완료한다.

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
| 다음 | 계좌 인증 완료 | `/merchant/register/pin` |
| 이전 | - | `/merchant/register/business` |

---

### M-REG-06 가맹점 가입: PIN 입력

- type: page
- route: `/merchant/register/pin`
- auth: public
- purpose: 결제 및 민감 작업에 사용할 PIN을 입력한다.

**actions**

| trigger | result |
| --- | --- |
| PIN 입력 완료 | `/merchant/register/pin-confirm` |
| 이전 | `/merchant/register/account` |

---

### M-REG-07 가맹점 가입: PIN 재입력

- type: page
- route: `/merchant/register/pin-confirm`
- auth: public
- purpose: PIN을 재입력해 일치 여부를 확인한다.

**actions**

| trigger | condition | result |
| --- | --- | --- |
| PIN 재입력 완료 | 최초 PIN과 일치 | `/merchant/register/processing` |
| PIN 재입력 완료 | 불일치 | 현재 화면에서 오류 표시 후 재입력 |
| 이전 | - | `/merchant/register/pin` |

---

### M-REG-08 가맹점 가입: 회원가입 처리중

- type: page
- route: `/merchant/register/processing`
- auth: public
- purpose: 가맹점 회원가입 요청을 최종 처리한다.
- initial state: loading

**states**

| state | contract |
| --- | --- |
| loading | 회원가입 처리중 |

**actions**

| trigger | condition | result |
| --- | --- | --- |
| 처리 성공 | - | `/merchant/register/complete` |
| 처리 실패 | - | `/merchant/register/account`로 돌아가 실패 사유 표시 |

---

### M-REG-09 가맹점 가입 완료

- type: page
- route: `/merchant/register/complete`
- auth: public
- purpose: 가맹점 회원가입 완료를 안내하고 가맹점 홈으로 진입한다.

**actions**

| trigger | result |
| --- | --- |
| 시작하기 | `/merchant/home` |
