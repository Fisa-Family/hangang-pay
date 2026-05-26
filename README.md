# hangang-pay

우리 FIS 아카데미 클라우드 서비스 6기 최종 프로젝트입니다.

사용자는 지역화폐를 충전해 가맹점에서 결제하고 은행 간 정산은 CBDC 기반 블록체인 컨트랙트로 처리합니다.

## 블록체인 컨트랙트 요약

블록체인 모듈은 `hangang-pay-bc`에 위치하며, Hyperledger Besu private network와 Hardhat 기반 Solidity 컨트랙트로 구성됩니다.

### 컨트랙트 구조

- `CBDCToken`: 한국은행이 발행하는 ERC-20 기반 CBDC입니다. 실제 CBDC 유동성은 `Settlement` 컨트랙트에 lock되어 기관 간 reserve 정산의 기반 자산으로 사용됩니다.
- `DepositToken`: 우리은행 예금 토큰입니다. 사용자 충전 시 mint되고 환불 시 burn되며, 결제 시 사용자와 가맹점 사이를 이동하는 실제 사용자 보유 토큰입니다.
- `Settlement`: 기관별 CBDC reserve를 내부 장부로 관리하고, 타행 충전/환불 시 기관 간 reserve 이동을 처리합니다.
- `LocalCurrencyPolicy`: 지역화폐 정책 레이어입니다. 가맹점 whitelist, 누적 발행 한도, 충전, 환불, 결제, 결제 취소를 담당하며 `DepositToken`과 `Settlement`를 호출합니다.

### UUPS Proxy 구조

컨트랙트는 UUPS Proxy 방식으로 배포됩니다. 이 방식에서는 주소가 두 종류로 나뉩니다.

- `Proxy 주소`: Spring Bank와 DB가 사용하는 고정 주소입니다. 사용자는 항상 이 주소로 컨트랙트를 호출합니다.
- `Implementation 주소`: 실제 함수 코드가 배포된 주소입니다. 컨트랙트 로직을 수정하면 이 주소만 새로 바뀝니다.

Spring Bank는 DB에 저장된 proxy 주소를 호출하고, proxy는 현재 연결된 implementation의 코드를 실행합니다.

```text
Spring Bank
→ Proxy 주소(고정)
→ 현재 연결된 Implementation 코드 실행
```

즉, 서비스가 사용하는 컨트랙트 주소는 유지하면서 내부 실행 로직만 교체할 수 있습니다.

### 서비스 호출 흐름

서비스 호출 흐름은 Spring Bank 서버가 DB에 저장된 `LocalCurrencyPolicy` proxy 주소를 조회해 `charge`, `refund`, `pay`, `cancelPayment`를 호출하는 방식입니다. `LocalCurrencyPolicy`는 내부에서 `DepositToken`의 mint/burn/forceTransfer와 `Settlement`의 reserve 이동을 수행합니다.

### 초기 배포

초기 배포는 `hangang-pay-bc/blockchain/scripts/deploy-uups.ts`가 담당합니다. 실행 시 각 컨트랙트의 proxy 주소와 implementation 주소가 새로 생성됩니다.

배포 스크립트는 초기 CBDC 유동성, 은행 reserve, operator 권한을 설정한 뒤 Spring Bank API를 호출해 DB에 proxy 주소를 저장합니다. Spring Bank는 이후 이 proxy 주소를 사용해 컨트랙트를 호출합니다.

### 업그레이드

컨트랙트 로직 변경 시에는 `deploy-uups.ts`를 다시 실행하지 않습니다. 대신 `upgrade-uups.ts`를 실행해 기존 proxy 주소는 그대로 두고, proxy가 실행할 implementation 코드만 새 버전으로 바꿉니다.

이 경우 Spring Bank가 호출하는 proxy 주소와 기존 on-chain state는 유지됩니다. 따라서 Spring DB의 `contract.address`는 변경하지 않습니다.

자세한 실행 방법과 컨트랙트별 흐름은 [`hangang-pay-bc/README.md`](./hangang-pay-bc/README.md)를 참고하세요.
