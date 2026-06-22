# Hangang-Pay

<img width="1920" height="1080" alt="image" src="https://github.com/user-attachments/assets/09b5e227-f1c1-4efb-a4db-7ddb8c0b682b" />

ex) 프로젝트 소개문
2026 우리FIS 아카데미 최종 프로젝트 1등 수상작!
당신의 일상이 금리를 결정합니다, FLEXRATE!

FLEXRATE는 국내 최초로 고객의 소비 패턴을 자체 AI가 분석하여, 건전한 소비는 금리 인하로, 과소비는 금리 상승으로 즉각 반영하는 동적 금리 시스템을 도입했습니다. 이를 통해 청년층이 자신의 소비 습관에 따라 직접 금리를 관리할 수 있으며, 기존의 전통적인 신용평가와 다른 새로운 금융 경험을 제공합니다.

---

## 🧑‍💻 기여자  
| 이름     | Github 프로필     | 역할                         |
|----------|------------------|-----------------------------|
| 남인서   | [sene03](https://github.com/sene03) | PM, FrontEnd, Backend,                      |
| 유승준   | [fluanceifi](https://github.com/fluanceifi) | TL, Backand, QA           |
| 서지원   | [seonjiwon](https://github.com/seonjiwon) |  Backend, infra            |
| 정가은   | [khmandarrin](https://github.com/khmandarrin) | BlockChain       |
| 류경록   | [pauly00](https://github.com/pauly00) | Frontend, Backend    |

---

## 🗓️ WBS
- WBS 이미지 넣기
---

## 🛠️ 기술 스택 및 서비스 아키텍처
<img width="2529" height="1079" alt="image" src="https://github.com/user-attachments/assets/30483f96-ae07-494e-a499-1095390407aa" />


<img width="5848" height="5628" alt="image" src="https://github.com/user-attachments/assets/53a3db77-0ecb-4d68-984f-cf9bdf192e7e" />


---

## 📋 ERD - 플랫폼 & 은행
<img width="2641" height="1933" alt="HANGANG" src="https://github.com/user-attachments/assets/affea737-ff07-4c2e-b232-8792e9d4ee76" />

---

## 🗒️ flexrate만의 신용 점수 및 우대 금리 평가 지표 (다른거 채우기)
![image](https://github.com/user-attachments/assets/7f633118-44ae-4673-b42b-0f726e541a5b)
![image](https://github.com/user-attachments/assets/890fc886-9e70-4e7d-a129-eba398bb1e2b)

---

## 📱 서비스 주요 기능
### 메인
- 메인 화면에 노출되는 상환액은 사용자의 대출 원금, 최신 금리, 총 대출 기간을 통해 원리금 균등 상환 공식으로 산출

<img width="300" alt="image" src="https://github.com/user-attachments/assets/b2f95baa-b800-4faf-b67b-64900b2b02fd" />

---
### 회원가입/로그인
- Google Mail SMTP를 활용한 이메일 인증

<img width="300" alt="image" src="https://github.com/user-attachments/assets/21eb7378-7990-41bd-a493-625ed1f2b411" />

---
### 신용 점수 산정
- NICE 기반 기존 신용평가 방식과 자체 소비 패턴을 종합적으로 반영해 신용 점수 산출
<img width="300" alt="image" src="https://github.com/user-attachments/assets/0d6600c9-f37b-464e-89d8-21c4f36b8ecc" />
<img width="800" alt="image" src="https://github.com/user-attachments/assets/983e637d-a34a-4481-9029-07b40aefd316" />

---
### 대출 신청
- 대출 신청시, 초기 금리를 예측해주기 위해 실제 카드 소비 데이터 10만건을 바탕으로 머신러닝 기반 금리 예측 모델 구현하여 사용
- 민감한 금융 거래 단계에서는 추가로 6자리 PIN번호 인증을 적용해 다중 인증 MFA 체계 구현

<img width="300" alt="image" src="https://github.com/user-attachments/assets/d45f78d9-aa3c-4b8d-a286-daa690f035fe" />
<img width="800" alt="image" src="https://github.com/user-attachments/assets/45d4be73-762f-4f2c-9eab-966632b097f8" />

---
### 대출 승인
<img width="800" alt="image" src="https://github.com/user-attachments/assets/dc0b5ae4-65c2-4f31-864d-08762fc788d8" />

---
### 소비습관 리포트
- 매월 1일 자정마다 스케줄러가 자동으로 실행되어, 각 회원의 한 달 소비 데이터를 OpenAI로 분석한 맞춤형 소비습관 리포트도 생성
- 소비습관 리포트의 카테고리별 소비 통계 컬럼은 json type으로 관리하여 복잡한 통계 정보를 효율적으로 다룸과 동시에 공간 효율성 상승

<img width="300" alt="image" src="https://github.com/user-attachments/assets/1f5ca916-e111-4425-9168-cd250b3dedd7" />

---
### 관리자 페이지 필터 및 대출 상태 변환
- 대출 상태는 '신청, 심사, 거절, 실행, 종료' 총 5개로 관리
- 관리자 페이지에서의 조회 API는 Spring Data의 페이징 기능을 활용해 대용량 데이터도 빠르게 조회할 수 있도록 구현
- 복잡한 검색 조건과 동적 쿼리 처리를 위해 QueryDSL 사용
- 필터 기능을 위해 BooleanBuilder와 CaseBuilder를 조합해, 각 조건이 있을 때만 쿼리에 반영되도록 설계

<img width="800" alt="image" src="https://github.com/user-attachments/assets/892eef2a-2b00-4a10-a78b-ea6084eae1ad" />

---
### 관리자 페이지 정보 수정
<img width="800" alt="image" src="https://github.com/user-attachments/assets/99083759-4fc8-4d01-b9fd-a5dfa4afcbcc" />

---
### 실시간 알림
- 대출 승인, 금리 변동 등 주요 이벤트는 SSE 기반 실시간 알림으로 제공

<img width="800" alt="image" src="https://github.com/user-attachments/assets/6092eba6-a0eb-430b-a6ee-9d737a659dd3" />

---
### 마이페이지
<img width="300" alt="image" src="https://github.com/user-attachments/assets/179f46fe-a540-451d-98e2-cc3e7dfecd01" />

---
### 커스텀 Exception
<img width="800" alt="image" src="https://github.com/user-attachments/assets/fb34f3ff-1e23-4c4f-992d-013dbe449b4a" />

---
### DDL 버전 관리
<img width="800" alt="image" src="https://github.com/user-attachments/assets/fd483c89-2a9b-4539-9a2e-fd9f6ad41b6f" />

---
### 실시간 로그 수집 및 분석 
- sentry 멘트 
- grafana cloud 멘트 

<img width="550" height="606" alt="image" src="https://github.com/user-attachments/assets/262a5e1c-d9dc-4bb5-b5f3-7f79c2f92d91" />


<img width="879" height="548" alt="image" src="https://github.com/user-attachments/assets/db62e4dd-5c87-4ba7-9b40-c4afa4f23b06" />

---
## 산출물
### ERD
<img width="800" alt="image" src="https://github.com/user-attachments/assets/5c964099-137d-47de-9134-f8f24400799a" />

### 총 53개의 API
- Swagger를 통한 실시간 문서화와 검증

<img width="10120" height="6360" alt="image" src="https://github.com/user-attachments/assets/9c4088f3-a198-440e-8228-de098854e1c2" />

### 총 43개의 UI
- 와이어프레임부터 컴포넌트 단위까지 일관된 디자인 시스템 적용

<img width="2246" height="750" alt="image" src="https://github.com/user-attachments/assets/89856659-b3d2-4605-8ed7-f3a6389c7cc8" />

---
## 회고
<img width="1472" alt="스크린샷 2025-06-11 00 10 29" src="https://github.com/user-attachments/assets/77f96d6d-5d8f-4582-aa6f-6e053058f37c" />




