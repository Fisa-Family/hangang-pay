## 개발 시 유의사항
```solidity
pragma solidity 0.8.28
```
정확한 버전을 사용해서 컨트랙트 코드를 작성해주세요. - 해당 버전 선택 이유 ? 말해야함

## 실행하기

Requirements:

- Node.js v22
- Docker
- Spring Boot 서버 실행 가능 상태

---

### 1. 컨트랙트 의존성 설치 및 컴파일

`hangang-pay/hangang-pay-bc/blockchain` 경로에서 실행합니다.

```bash
cd hangang-pay/hangang-pay-bc/blockchain

npm i

npx hardhat compile
```

> Hardhat deploy/upgrade 실행 시 자동 compile이 수행될 수 있지만,  
> 컨트랙트 변경 후에는 명시적으로 compile을 먼저 실행하는 것을 권장합니다.

---

### 2. 블록체인 노드 실행

`hangang-pay/hangang-pay-bc/network` 경로에서 실행합니다.

```bash
cd hangang-pay/hangang-pay-bc/network

docker compose up -d
```

---

### 3. Spring 서버 실행

Hardhat 배포 스크립트가 배포 결과를 Spring 내부 API로 전달하므로,  
`hangang-pay-bank` 서버가 먼저 실행되어 있어야 합니다.

```bash
cd hangang-pay/hangang-pay-bank

./gradlew bootRun
```

---

### 4. UUPS Proxy 컨트랙트 최초 배포

`hangang-pay/hangang-pay-bc/blockchain` 경로에서 실행합니다.

```bash
cd hangang-pay/hangang-pay-bc/blockchain

npx hardhat run scripts/deploy-uups.ts --network besu
```

`deploy-uups.ts`는 최초 환경 세팅 또는 체인 초기화 후 재배포용 스크립트입니다.

실행 시 다음 작업을 수행합니다.

1. CBDCToken proxy 배포
2. DepositToken proxy 배포
3. Settlement proxy 배포
4. LocalCurrencyPolicy proxy 배포
5. CBDC를 Settlement proxy에 mint
6. 은행 기관 등록
7. 기관별 reserve 설정
8. LocalCurrencyPolicy에 operator 권한 설정
9. 배포 결과를 `deployments/uups-latest.json`에 저장
10. Spring 내부 API로 proxy 주소 동기화

> 이 스크립트를 다시 실행하면 업그레이드가 아니라 재배포입니다.  
> 새로운 proxy 주소가 생성되므로 DB의 `contract.address`도 새 proxy 주소로 갱신됩니다.

---

### 5. 컨트랙트 업그레이드

컨트랙트 로직을 수정한 뒤 기존 proxy 주소를 유지하면서 implementation만 교체하려면 upgrade 스크립트를 실행합니다.

```bash
cd hangang-pay/hangang-pay-bc/blockchain

npx hardhat compile

CONTRACT_TYPE=LOCAL_CURRENCY \
npx hardhat run scripts/upgrade-uups.ts --network besu
```

`CONTRACT_TYPE` 값은 아래 중 하나를 사용합니다.

```text
CBDC
DEPOSIT_TOKEN
SETTLEMENT
LOCAL_CURRENCY
```

업그레이드 시:

- proxy 주소는 유지됩니다.
- implementation 주소만 변경됩니다.
- 기존 storage/state는 유지됩니다.
- Spring DB의 `contract.address`는 proxy 주소이므로 변경하지 않습니다.
- 업그레이드 결과는 `deployments/uups-upgrade-latest.json`에 저장됩니다.

---

## 블록체인 데이터 초기화

노드 데이터가 꼬였거나 체인을 초기화해야 하는 경우 아래 명령어를 실행합니다.

`hangang-pay/hangang-pay-bc/network` 경로에서 실행합니다.

```bash
find node-1/data node-2/data node-3/data node-4/data \
  -mindepth 1 \
  ! -name key \
  ! -name key.pub \
  -exec rm -rf {} +
```

> validator key / public key 파일은 유지하고 나머지 블록체인 데이터를 초기화합니다.
>
> 체인을 초기화하면 기존 proxy 주소와 컨트랙트 상태도 사라지므로,  
> `deploy-uups.ts`를 다시 실행하고 Spring DB의 contract 주소도 다시 동기화해야 합니다.

---

## 스크립트

- [reset.sh](./network/scripts/reset.sh) : 도커 볼륨 삭제 및 연결된 로컬 파일 삭제. 블록체인 네트워크 초기화 시 사용합니다. 네트워크 초기화 시 연관된 엔티티가 존재하므로 backend DB 초기화가 필요할 수 있습니다. 예: institution, contract 엔티티
- [logs.sh](./network/scripts/logs.sh) : 4개 노드 로그 출력

---

## TODO

온프레미스 또는 AWS 세팅 시 private key를 새로 생성해야 합니다. 현재 genesis.json에 있는 키를 그대로 사용하면 안 됩니다.

TODO: 토큰 초기 발행량 근거 작성하기

---

## UUPS Proxy 구조

현재 컨트랙트는 UUPS Proxy 방식으로 배포됩니다.

기존 일반 컨트랙트 배포 방식과 달리, 실제 서비스와 DB는 implementation 주소가 아니라 proxy 주소를 사용합니다.

```text
Spring / 사용자 호출
→ Proxy 주소
→ 현재 Implementation으로 delegatecall
```

최초 배포 시에는 `deploy-uups.ts`가 proxy와 implementation을 함께 배포합니다.

업그레이드 시에는 `upgrade-uups.ts`가 기존 proxy 주소는 유지한 채 새로운 implementation만 배포하고 proxy가 바라보는 implementation 주소를 교체합니다.

따라서 DB의 `contract.address`에는 항상 proxy 주소를 저장합니다.

---

## CBDC 초기 유동성 및 준비금 설정

시뮬레이션 환경에서는 한국은행(BoK)이 충분한 규모의 CBDC 유동성을 Settlement 컨트랙트에 초기 예치합니다.

이 유동성은 기관 간 정산 및 기관별 준비금(reserve)의 기반 자산 역할을 수행합니다.

이후 각 참여 기관에는 Settlement 컨트랙트 내부 장부 기준으로 초기 CBDC 준비금이 배정됩니다.

해당 준비금 규모는 실제 지급준비율이나 통화정책을 모델링하기 위한 목적이 아니라, 충전·환불·결제 등 정산 흐름이 원활하게 수행될 수 있도록 충분한 유동성을 제공하기 위한 시뮬레이션용 설정입니다.

---

## 컨트랙트 구조

### 1. CBDC Token

한국은행(BoK)이 발행하는 ERC-20 기반 중앙은행 디지털화폐입니다.

#### 역할

- 기관 간 최종 정산 자산
- Settlement 컨트랙트 내부 reserve의 기반 자산
- 한국은행이 발행 및 관리

#### 특징

- ERC-20 기반
- 실제 CBDC는 Settlement 컨트랙트에 lock되어 관리됨
- 기관 간 정산 시 실제 가치 이전 수단으로 사용
- 사용자 직접 결제에는 사용되지 않음
- LocalCurrencyPolicy 및 사용자 계층에서는 직접 사용하지 않음

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
- LocalCurrencyPolicy 컨트랙트가 operator 권한을 가짐

---

### 3. Settlement

기관 간 CBDC reserve 정산을 담당하는 공용 인프라 컨트랙트입니다.

#### 역할

- 기관별 CBDC reserve 관리
- 기관 간 CBDC reserve 이동 처리
- Settlement 내부 reserve 장부 관리

#### 특징

- 실제 CBDC는 Settlement 컨트랙트에 lock됨
- 기관별 reserve는 reserveBalance 내부 장부로 관리
- 기관 간 CBDC 이동을 추상화하여 처리
- DepositToken mint/burn 및 지역화폐 정책 로직은 담당하지 않음
- LocalCurrencyPolicy가 reserve 이동 요청 시 정산만 수행

#### reserve 이동 흐름

1. LocalCurrencyPolicy에서 reserve 이동 요청
2. Settlement.moveReserve 호출
3. 송신 기관 reserve 차감
4. 수신 기관 reserve 증가
5. 기관 간 CBDC reserve 정산 완료

---

### 4. LocalCurrencyPolicy

지역화폐 서비스 및 결제 정책 컨트랙트입니다.

#### 역할

- 지역화폐 충전 및 환불 처리
- 지역화폐 결제 및 결제 취소 처리
- 가맹점 등록 및 해제 관리
- 지역화폐 누적 발행 한도 관리
- Settlement를 통한 CBDC reserve 정산 요청

#### 특징

- ERC-20이 아님
- DepositToken 기반 서비스 정책 레이어
- 등록된 가맹점에서만 결제 가능
- 지역화폐 누적 발행 한도 제한
- 충전/환불 시 Settlement에 reserve 이동 요청
- DepositToken mint/burn/forceTransfer 권한 보유
- 결제 취소 시 가맹점에서 사용자에게 토큰 반환 가능

#### 가맹점 등록 흐름

1. 관리자 가맹점 등록 요청
2. LocalCurrencyPolicy.setMerchant 호출
3. 가맹점 whitelist 등록

#### 충전 흐름 (현금 → DepositToken)

1. 사용자 현금 입금
2. LocalCurrencyPolicy.charge 호출
3. 등록된 기관인지 검증
4. 누적 지역화폐 발행 한도 검증
5. 타행 충전인 경우 Settlement.moveReserve 호출
6. 기관 reserve 정산 처리
7. DepositToken.mint 실행
8. 사용자 DepositToken 발행
9. totalIssued 증가

#### 환불 흐름 (DepositToken → 현금)

1. 사용자 환불 요청
2. LocalCurrencyPolicy.refund 호출
3. 등록된 기관인지 검증
4. DepositToken.burn 실행
5. 사용자 DepositToken 소각
6. 타행 환불인 경우 Settlement.moveReserve 호출
7. 기관 reserve 정산 처리

> totalIssued는 누적 발행량이므로 환불 시 감소하지 않습니다.

#### 결제 흐름

1. 사용자 결제 요청
2. LocalCurrencyPolicy.pay 호출
3. 수취 주소가 등록된 가맹점인지 검증
4. DepositToken.forceTransfer 실행
5. 사용자 → 가맹점 토큰 이동

#### 결제 취소 흐름

1. 결제 취소 요청
2. LocalCurrencyPolicy.cancelPayment 호출
3. 송신 주소가 등록된 가맹점인지 검증
4. DepositToken.forceTransfer 실행
5. 가맹점 → 사용자 토큰 반환

---

## BE 연동 시 호출 기준

실제 서비스 도메인에서는 컨트랙트 함수를 직접 ABI encoding 하지 않고, `ContractCallService`의 메서드를 호출합니다.

`ContractCallService`는 내부에서 컨트랙트 함수명을 구성하고, `BlockchainTxService.sendFunctionTransaction(...)`을 통해 트랜잭션을 전송합니다.

Spring이 DB에서 조회하는 컨트랙트 주소는 implementation 주소가 아니라 proxy 주소입니다.

---

### 충전

LocalCurrencyPolicy 컨트랙트를 호출합니다.

#### BE 호출 함수

```java
contractCallService.charge(
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
| `institutionId` | 사용자가 충전할 때 선택한 은행 기관 ID |
| `userAddress` | 충전 대상 사용자 지갑 주소 |
| `amount` | 충전 금액 |

#### 역할

- 등록된 기관인지 검증
- 누적 지역화폐 발행 한도 검증
- 타행 충전 여부 검증
- 필요 시 Settlement.moveReserve 호출
- 선택 은행 reserve 차감
- 우리은행 reserve 증가
- 사용자에게 DepositToken mint

---

### 환불

LocalCurrencyPolicy 컨트랙트를 호출합니다.

#### BE 호출 함수

```java
contractCallService.refund(
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
| `institutionId` | 환불 받을 은행 기관 ID |
| `userAddress` | 환불 대상 사용자 지갑 주소 |
| `amount` | 환불 금액 |

#### 역할

- 등록된 기관인지 검증
- 사용자 DepositToken burn
- 타행 환불 여부 검증
- 필요 시 Settlement.moveReserve 호출
- 우리은행 reserve 차감
- 환불 대상 은행 reserve 증가

---

### 결제

LocalCurrencyPolicy 컨트랙트를 호출합니다.

#### BE 호출 함수

```java
contractCallService.pay(
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
| `userAddress` | 결제 사용자 지갑 주소 |
| `merchantAddress` | 결제 가맹점 지갑 주소 |
| `amount` | 결제 금액 |

#### 역할

- 수취 주소가 등록 가맹점인지 검증
- 사용자에서 가맹점으로 DepositToken 강제 이체

---

### 결제 취소

LocalCurrencyPolicy 컨트랙트를 호출합니다.

#### BE 호출 함수

```java
contractCallService.cancelPayment(
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
| `merchantAddress` | 결제 취소를 수행하는 가맹점 지갑 주소 |
| `userAddress` | 토큰을 돌려받는 사용자 지갑 주소 |
| `amount` | 결제 취소 금액 |

#### 역할

- 송신 주소가 등록 가맹점인지 검증
- 가맹점에서 사용자로 DepositToken 강제 이체

---

## 가맹점 등록 / 해제

LocalCurrencyPolicy 컨트랙트를 호출합니다.

#### BE 호출 함수

```java
contractCallService.setMerchant(
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

| 대상 컨트랙트 | Operator |
|---|---|
| Settlement | LocalCurrencyPolicy |
| DepositToken | LocalCurrencyPolicy |

이를 통해 LocalCurrencyPolicy가 다음 기능을 수행할 수 있습니다.

- DepositToken mint
- DepositToken burn
- DepositToken forceTransfer
- Settlement reserve 이동 요청

Settlement는 CBDC reserve 정산 인프라 역할만 수행하며, 실제 지역화폐 서비스 로직은 LocalCurrencyPolicy가 담당합니다.