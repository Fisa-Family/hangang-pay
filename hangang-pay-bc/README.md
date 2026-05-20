## 개발 시 유의사항
```solidity
pragma solidity 0.8.28
```
정확한 버전을 사용해서 컨트랙트 코드를 작성해주세요. - 해당 버전 선택 이유 ? 말해야함

## 실행하기
Requirements: node v22 

```bash
cd blockchain
npm i

cd network
docker compose up -d
```

## 스크립트
- [reset.sh](./network/scripts/reset.sh) : 도커 볼륨 삭제 및 연결된 로컬 파일 삭제. 블록체인 네트워크 초기화 시 사용하세요. 네트워크 초기화 시 연관된 엔티티가 존재하므로 backend db 초기화가 필요할 수 있습니다. (ex. institution, contract 엔티티)
- [logs.sh](./network/scripts/logs.sh) : 4개 노드 로그 출력


## TODO
온프레미스 또는 AWS 세팅 시 private key 새로 생성해야합니다. 현재 genesis.json에 있는 키 그대로 쓰면 안됩니다.

TODO: 토큰 초기 발행량 근거 작성하기

## CBDC 초기 유동성 및 준비금 설정

시뮬레이션 환경에서는 한국은행(BoK)이 충분한 규모의 CBDC 유동성을 Settlement 컨트랙트에 초기 예치합니다.

이 유동성은 기관 간 정산 및 기관별 준비금(reserve)의 기반 자산 역할을 수행합니다.

이후 각 참여 기관에는 Settlement 컨트랙트 내부 장부 기준으로 초기 CBDC 준비금이 배정됩니다.

해당 준비금 규모는 실제 지급준비율이나 통화정책을 모델링하기 위한 목적이 아니라,
충전·환불·결제 등 정산 흐름이 원활하게 수행될 수 있도록 충분한 유동성을 제공하기 위한 시뮬레이션용 설정입니다.

## 컨트랙트 구조

### 1. CBDC Token

한국은행(BoK)이 발행하는 ERC-20 기반 중앙은행 디지털화폐입니다.

#### 역할
- 기관 간 최종 정산 자산
- Settlement 컨트랙트 내부 reserve의 기반 자산
- 한국은행이 발행 및 관리

#### 특징
- Settlement 컨트랙트가 operator 권한을 가짐
- 기관 간 정산 시 실제 가치 이전 수단으로 사용
- 사용자 직접 결제에는 사용되지 않음

---

### 2. DepositToken

우리은행 예금 토큰입니다.

#### 역할
- 사용자 예금 잔액 표현
- 충전/환불 및 결제에 사용되는 사용자 보유 토큰

#### 특징
- ERC-20 기반
- 충전 시 mint
- 환불 시 burn
- Settlement 및 LocalCurrencyPolicy 컨트랙트가 operator 권한을 가짐

---

### 3. Settlement

기관 간 CBDC 정산을 담당하는 핵심 컨트랙트입니다.

#### 역할
- 기관별 CBDC reserve 관리
- 충전/환불 시 CBDC 정산 처리
- DepositToken mint/burn 제어

#### 특징
- 실제 CBDC는 Settlement 컨트랙트에 lock됨
- 기관별 reserve는 reserveBalance 내부 장부로 관리
- 기관 간 CBDC 이동을 추상화하여 처리

#### 충전 흐름 (현금 → DepositToken)

1. 사용자 현금 입금
2. Settlement 호출
3. 기관 reserve 감소
4. 사용자 DepositToken mint

#### 환불 흐름 (DepositToken → 현금)

1. 사용자 DepositToken 반환
2. Settlement 호출
3. 사용자 DepositToken burn
4. 기관 reserve 증가

---

### 4. LocalCurrencyPolicy

지역화폐 결제 정책 컨트랙트입니다.

#### 역할
- 지역화폐 결제 정책 관리
- 가맹점 등록 및 해제 관리
- 가맹점 제한 및 사용 한도 관리
- 지역화폐 결제 및 결제 취소 처리

#### 특징
- ERC-20이 아님
- DepositToken 기반 정책 레이어
- 등록된 가맹점에서만 결제 가능
- 총 사용 한도 제한 가능
- 결제 취소 시 가맹점에서 사용자에게 토큰 반환 가능

#### 가맹점 등록 흐름

1. 관리자 가맹점 등록 요청
2. LocalCurrencyPolicy.setMerchant 호출
3. 가맹점 whitelist 등록

#### 결제 흐름

1. 사용자 결제 요청
2. LocalCurrencyPolicy.pay 호출
3. 수취 주소가 등록된 가맹점인지 검증
4. 총 사용 한도 검증
5. DepositToken.forceTransfer 실행
6. 사용자 → 가맹점 토큰 이동
7. 누적 사용 금액 증가

#### 결제 취소 흐름

1. 결제 취소 요청
2. LocalCurrencyPolicy.cancelPayment 호출
3. 송신 주소가 등록된 가맹점인지 검증
4. DepositToken.forceTransfer 실행
5. 가맹점 → 사용자 토큰 반환
6. 누적 사용 금액 감소

---

## BE 연동 시 호출 기준

실제 서비스 도메인에서는 컨트랙트 함수를 직접 ABI encoding 하지 않고,  
`ContractCallService`의 메서드를 호출합니다.

`ContractCallService`는 내부에서 컨트랙트 함수명을 구성하고,  
`BlockchainTxService.sendFunctionTransaction(...)`을 통해 트랜잭션을 전송합니다.

---

### 충전

Settlement 컨트랙트를 호출합니다.

#### BE 호출 함수

```java
contractCallService.charge(
        web3j,
        credentials,
        settlementAddress,
        institutionId,
        userAddress,
        amount
);
```

#### 실제 호출되는 컨트랙트 함수

```solidity
charge(institutionId, userAddress, amount)
```

#### 파라미터 의미

| 파라미터 | 의미 |
|---|---|
| `settlementAddress` | Settlement 컨트랙트 주소 |
| `institutionId` | 사용자가 충전할 때 선택한 은행 기관 ID |
| `userAddress` | 충전 대상 사용자 지갑 주소 |
| `amount` | 충전 금액 |

#### 역할
- 선택 은행 reserve 차감
- 우리은행 reserve 증가
- 사용자에게 DepositToken mint

---

### 환불

Settlement 컨트랙트를 호출합니다.

#### BE 호출 함수

```java
contractCallService.refund(
        web3j,
        credentials,
        settlementAddress,
        institutionId,
        userAddress,
        amount
);
```

#### 실제 호출되는 컨트랙트 함수

```solidity
refund(institutionId, userAddress, amount)
```

#### 파라미터 의미

| 파라미터 | 의미 |
|---|---|
| `settlementAddress` | Settlement 컨트랙트 주소 |
| `institutionId` | 환불 받을 은행 기관 ID |
| `userAddress` | 환불 대상 사용자 지갑 주소 |
| `amount` | 환불 금액 |

#### 역할
- 사용자 DepositToken burn
- 우리은행 reserve 차감
- 환불 대상 은행 reserve 증가

---

### 결제

LocalCurrencyPolicy 컨트랙트를 호출합니다.

#### BE 호출 함수

```java
contractCallService.pay(
        web3j,
        credentials,
        localCurrencyAddress,
        userAddress,
        merchantAddress,
        amount
);
```

#### 실제 호출되는 컨트랙트 함수

```solidity
pay(userAddress, merchantAddress, amount)
```

#### 파라미터 의미

| 파라미터 | 의미 |
|---|---|
| `localCurrencyAddress` | LocalCurrencyPolicy 컨트랙트 주소 |
| `userAddress` | 결제 사용자 지갑 주소 |
| `merchantAddress` | 결제 가맹점 지갑 주소 |
| `amount` | 결제 금액 |

#### 역할
- 수취 주소가 등록 가맹점인지 검증
- 총 사용 한도 검증
- 사용자에서 가맹점으로 DepositToken 강제 이체
- 누적 사용 금액 증가

---

### 결제 취소

LocalCurrencyPolicy 컨트랙트를 호출합니다.

#### BE 호출 함수

```java
contractCallService.cancelPayment(
        web3j,
        credentials,
        localCurrencyAddress,
        merchantAddress,
        userAddress,
        amount
);
```

#### 실제 호출되는 컨트랙트 함수

```solidity
cancelPayment(merchantAddress, userAddress, amount)
```

#### 파라미터 의미

| 파라미터 | 의미 |
|---|---|
| `localCurrencyAddress` | LocalCurrencyPolicy 컨트랙트 주소 |
| `merchantAddress` | 결제 취소를 수행하는 가맹점 지갑 주소 |
| `userAddress` | 토큰을 돌려받는 사용자 지갑 주소 |
| `amount` | 결제 취소 금액 |

#### 역할
- 송신 주소가 등록 가맹점인지 검증
- 가맹점에서 사용자로 DepositToken 강제 이체
- 누적 사용 금액 감소

---

## 가맹점 등록 / 해제


#### BE 호출 함수

```java
contractCallService.setMerchant(
        web3j,
        credentials,
        localCurrencyAddress,
        merchantAddress,
        true
);
```

#### 실제 호출되는 컨트랙트 함수

```solidity
setMerchant(merchantAddress, true)
```

#### 가맹점 해제

```java
contractCallService.setMerchant(
        web3j,
        credentials,
        localCurrencyAddress,
        merchantAddress,
        false
);
```

```solidity
setMerchant(merchantAddress, false)
```

#### 역할
- `true`: 가맹점 whitelist 등록
- `false`: 가맹점 whitelist 해제

---

## Operator 권한 구조

배포 시 다음 operator 권한이 자동 설정됩니다.

| 대상 Token | Operator |
|---|---|
| CBDC | Settlement |
| DepositToken | Settlement |
| DepositToken | LocalCurrencyPolicy |

이를 통해 Settlement와 LocalCurrencyPolicy가  
mint / burn / forceTransfer를 수행할 수 있습니다.