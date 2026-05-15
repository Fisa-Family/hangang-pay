# 가맹점 앱 화면 명세

대상 화면: M-01, M-QR-_, M-PAY-_, M-SET-_, M-MY-_

---

## 가맹점 홈

### M-01 가맹점 홈

- 타입: page
- 경로: /merchant/home

**UI**

- 가맹점명
- KPI 카드 (2열 그리드):
  - 오늘 매출
  - 결제 건수
- 빠른 실행 버튼: 내 QR 보기, 결제 내역, 정산 내역, 정산받기
- 하단 내비: → C-NAV-MERCHANT (활성: 홈)

**액션**
| 트리거 | 이동 |
|--------|------|
| 내 QR 보기 | /merchant/qr |
| 결제 내역 | /merchant/payments |
| 정산 내역 | /merchant/settlements |
| 정산받기 | /merchant/settlements/amount |

---

## 가맹점 QR

### M-QR-01 가맹점 QR

- 타입: page
- 경로: /merchant/qr

**UI**

- QR 코드 (대형)
- 가맹점명
- 안내 문구: 사용자가 이 QR을 스캔해 결제합니다
- 가맹점 지갑 주소 하단에 보여주기

**액션**
| 트리거 | 이동 |
|--------|------|
| 뒤로 | /merchant/home |

---

## 결제 내역 / 취소 플로우

### M-PAY-01 가맹점 결제 내역

- 타입: page
- 경로: /merchant/payments

**UI**

- 기간 필터 탭: 일별, 주별, 월별
- 결제 리스트:
  - 사용자 마스킹 정보
  - 금액
  - 상태 배지: 결제 완료 (green) / 취소됨 (gray/red)
  - 결제 일시

**액션**
| 트리거 | 이동 |
|--------|------|
| 결제 항목 탭 | /merchant/payments/:id |
| 뒤로 | /merchant/home |

---

### M-PAY-02 결제 상세

- 타입: page
- 경로: /merchant/payments/:id

**UI**

- 결제 금액
- 사용자 마스킹 정보
- 승인번호
- 결제 일시
- 취소 가능 여부 안내 (24시간 이내 취소 가능)
- 버튼: 결제 취소하기

**모달**: 결제 취소하기 탭 시 인라인 오버레이 (별도 route 없음)

- 메시지: 이 결제를 취소하시겠습니까?
- 취소 금액, 결제 정보(시간, 트랜잭션 해시 등)
- 버튼: 아니오, 예

**액션**
| 트리거 | 이동 |
|--------|------|
| 결제 취소하기 | 현재 화면 위 취소 확인 모달 열기 |
| 취소 확인 모달: 예 | /merchant/payments/:id/cancel/processing |
| 취소 확인 모달: 아니오 | 현재 화면 (모달 닫기) |
| 뒤로 | /merchant/payments |

---

### M-PAY-04 취소 처리중

- 타입: page
- 경로: /merchant/payments/:id/cancel/processing
- 초기 상태: loading

**상태별 UI**
| 상태 | 렌더 |
|------|------|
| loading | 스피너 + "결제를 취소하고 있어요" + 취소 금액 |
| error | 오류 아이콘 + "결제를 취소하지 못했습니다" + 실패 사유 + 취소 금액 + 버튼: 홈으로 |

**액션**
| 트리거 | 조건 | 이동 |
|--------|------|------|
| 처리 성공 | - | /merchant/payments/:id/cancel/complete |
| 처리 실패 | - | 현재 화면 error 상태 |
| 홈으로 | error 상태 | /merchant/home |

---

### M-PAY-05 취소 완료

- 타입: page
- 경로: /merchant/payments/:id/cancel/complete

**UI**

- 완료 아이콘
- 취소 금액
- 상태: 취소 완료
- 트랜잭션 해시
- 버튼: 홈으로

**액션**
| 트리거 | 이동 |
|--------|------|
| 홈으로 | /merchant/home |

---

## 정산 플로우

### M-SET-01 가맹점 정산 내역

- 타입: page
- 경로: /merchant/settlements

**UI**

- 기간 필터 탭: 일별, 월별
- 정산 리스트:
  - 정산 금액
  - 상태 배지: 완료 (green) / 실패 (red)
  - 신청 일시
  - 완료 일시 (완료된 경우)
- 버튼: 정산받기

**액션**
| 트리거 | 이동 |
|--------|------|
| 정산받기 | /merchant/settlements/amount |
| 뒤로 | /merchant/home |

---

### M-SET-02 정산 가능 금액 확인

- 타입: page
- 경로: /merchant/settlements/amount

**UI**

- 정산 가능 금액
- 정산 계좌: 은행명 + 마스킹 계좌번호
- 금액 입력 필드 (클릭 시 숫자패드)
- 버튼: 정산 신청, 이전

**액션**
| 트리거 | 이동 |
|--------|------|
| 정산 신청 | /merchant/settlements/confirm |
| 이전 | router.back() — /merchant/home 또는 /merchant/settlements (진입 경로 따름) |

---

### M-SET-03 정산 신청 확인

- 타입: page
- 경로: /merchant/settlements/confirm

**UI**

- 요약 카드:
  - 정산 금액
  - 최종 입금 금액
  - 정산 계좌: 은행명 + 마스킹 계좌번호
- 버튼: 신청하기, 이전

**액션**
| 트리거 | 이동 |
|--------|------|
| 신청하기 | /merchant/settlements/processing |
| 이전 | /merchant/settlements/amount |

---

### M-SET-04 정산 처리중

- 타입: page
- 경로: /merchant/settlements/processing
- 초기 상태: loading

**상태별 UI**
| 상태 | 렌더 |
|------|------|
| loading | 스피너 + "정산을 신청하고 있어요" + 정산 금액 |
| error | 오류 아이콘 + "정산 신청을 완료하지 못했습니다" + 실패 사유: "정산 가능 금액 또는 계좌 정보를 다시 확인해주세요" + 서버 오류 메시지 + 정산 금액 + 버튼: 다시 시도, 홈으로 |

**액션**
| 트리거 | 조건 | 이동 |
|--------|------|------|
| 처리 성공 | - | /merchant/settlements/complete |
| 처리 실패 | - | 현재 화면 error 상태 |
| 다시 시도 | error 상태 | /merchant/settlements/confirm |
| 홈으로 | error 상태 | /merchant/home |

---

### M-SET-05 정산 신청 완료

- 타입: page
- 경로: /merchant/settlements/complete

**UI**

- 완료 아이콘
- 정산 금액
- 정산 계좌: 은행명 + 마스킹 계좌번호
- 신청 일시
- 버튼: 홈으로

**액션**
| 트리거 | 이동 |
|--------|------|
| 홈으로 | /merchant/home |

---

## 가맹점 마이페이지

### M-MY-01 가맹점 마이페이지

- 타입: page
- 경로: /merchant/mypage

**UI**

- 가맹점명
- 사업자 등록번호
- 대표자명
- 메뉴:
  - 가맹점 정보
  - 정산 계좌 관리 (onClick 없음, 미구현)
  - 로그아웃
- 하단 내비: → C-NAV-MERCHANT (활성: 마이)

**액션**
| 트리거 | 이동 |
|--------|------|
| 가맹점 정보 | /merchant/mypage/info |
| 정산 계좌 관리 | 없음 (disabled) |
| 로그아웃 | C-LOGOUT-01 모달 열기 |

---

### M-MY-02 가맹점 정보 상세

- 타입: page
- 경로: /merchant/mypage/info

**UI**

- 상호명
- 사업자 등록번호
- 대표자명
- 사업장 주소
- 연락처

**액션**
| 트리거 | 이동 |
|--------|------|
| 뒤로 | /merchant/mypage |
