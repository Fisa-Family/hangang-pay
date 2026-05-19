# 충전 API 로컬 테스트 환경 설정

충전 실행 API (CHARGE-003) 를 로컬에서 테스트하기 위한 환경 설정 절차.
블록체인 노드 실행부터 컨트랙트 배포, DB 초기 데이터 삽입까지 순서대로 진행한다.

서버를 재시작할 때마다 3단계 DB 데이터 삽입부터 다시 수행해야 한다.
ddl-auto 가 create-drop 으로 설정된 경우 재시작 시 모든 테이블 데이터가 초기화된다.


## 사전 조건

- Docker 설치 및 실행 중
- Node.js 및 npm 설치
- Spring Boot 로컬 서버 실행 가능 상태


## 전체 순서

```
1단계  Besu 블록체인 노드 4개 실행
2단계  스마트 컨트랙트 컴파일 및 artifact 복사
3단계  DB 기관 데이터 삽입
4단계  컨트랙트 배포 API 호출
5단계  CBDC 배포 (각 은행에 CBDC 전송)
6단계  테스트용 DB 데이터 삽입
7단계  충전 API 테스트
```


## 1단계  Besu 블록체인 노드 실행

한국은행, 우리은행, 신한은행, 하나은행 총 4개의 Besu 노드를 Docker 컨테이너로 실행한다.

```bash
cd hangang-pay-bc/network
docker compose up -d
```

실행 확인

```bash
docker ps
```

노드별 포트 구성

| 노드 | 기관 | RPC 포트 |
| --- | --- | --- |
| node1 | 한국은행 (BoK) | 8545 |
| node2 | 우리은행 (WR) | 8547 |
| node3 | 신한은행 (SH) | 8549 |
| node4 | 하나은행 (HN) | 8551 |


## 2단계  스마트 컨트랙트 컴파일 및 artifact 복사

처음 한 번만 실행한다. 컨트랙트 코드가 변경된 경우 다시 실행한다.

```bash
cd hangang-pay-bc/blockchain
npm install
npx hardhat compile
./scripts/copy-artifacts.sh
```

컴파일 결과물이 hangang-pay-be/src/main/resources/contracts/ 로 복사된다.


## 3단계  DB 기관 데이터 삽입

Spring Boot 서버 실행 후 아래 SQL 을 실행한다.
서버를 재시작할 때마다 다시 실행해야 한다.

```sql
INSERT INTO institution (institution_code, institution_name, wallet_address, encrypted_private_key, rpc_endpoint, created_at, updated_at) VALUES
('BoK', '한국은행', '0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73', '8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63', 'http://localhost:8545', NOW(), NOW()),
('WR',  '우리은행', '0x627306090abaB3A6e1400e9345bC60c78a8BEf57', 'c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3', 'http://localhost:8547', NOW(), NOW()),
('SH',  '신한은행', '0xf17f52151EbEF6C7334FAD080c5704D77216b732', 'ae6ae8e5ccbfb04590405997ee2d52d2b330726137b875053c36d94e974d162f', 'http://localhost:8549', NOW(), NOW()),
('HN',  '하나은행', '0xE9BA79E62a58225065bF24313896CD332dAFCB3C', 'fdad4ce4c7c8382ea0357ad12071156ba54963cabed82f415e24c43f537fe784', 'http://localhost:8551', NOW(), NOW());
```

지갑 주소와 private key 는 hardhat.config.ts 의 테스트 계정 기준이다.


## 4단계  컨트랙트 배포 API 호출

Spring Boot 서버가 직접 블록체인에 컨트랙트를 배포하고 주소를 DB 에 저장한다.
서버를 재시작할 때마다 다시 호출해야 한다.

```bash
curl -X POST http://localhost:8080/institutions/contracts/deploy
```

또는 Swagger UI 에서 POST /institutions/contracts/deploy 실행.

배포 순서는 다음과 같다.

```
한국은행의 CBDC 컨트랙트 배포
각 은행의 DepositToken 컨트랙트 배포
한국은행의 Settlement 컨트랙트 배포
```

배포된 주소는 contract_address 테이블에 자동 저장된다.


## 5단계  CBDC 각 은행에 배포

CBDC 는 한국은행 지갑에만 발행되므로 각 은행 지갑으로 전송해야 한다.
이 과정이 없으면 발행 가능량이 0 이 되어 충전이 불가능하다.

배포된 CBDC 컨트랙트 주소를 DB 에서 조회한다.

```sql
SELECT ca.address
FROM contract_address ca
JOIN institution i ON ca.institution_id = i.id
WHERE i.institution_code = 'BoK' AND ca.name = 'CBDC';
```

조회한 주소를 환경변수로 넘겨 스크립트를 실행한다.

```bash
cd hangang-pay-bc/blockchain
CBDC_ADDRESS=조회한주소 npx hardhat run scripts/distribute-cbdc.ts --network besu
```

스크립트가 각 은행 지갑으로 1억 CBDC 를 전송한다.


## 6단계  테스트용 DB 데이터 삽입

충전 테스트에 필요한 계좌 및 지갑 데이터를 삽입한다.
서버를 재시작할 때마다 다시 실행해야 한다.

party 생성

```sql
INSERT INTO party (party_type, created_at, updated_at)
VALUES ('USER', NOW(), NOW());
```

wallet 생성 (party_id 는 위에서 생성된 id 로 교체)

```sql
INSERT INTO wallet (party_id, institution_id, address, created_at, updated_at)
SELECT 1, id, '0x000000000000000000000000000000000000dead', NOW(), NOW()
FROM institution WHERE institution_code = 'WR';
```

account 를 WR 기관으로 연결 (account id 와 party_id 는 실제 값으로 교체)

```sql
UPDATE account
SET institution_id = (SELECT id FROM institution WHERE institution_code = 'WR')
WHERE id = 13;
```

bank_account 잔액 삽입

```sql
INSERT INTO bank_account (institution_id, account_number, account_holder_name, balance, created_at, updated_at)
SELECT id, '1234567891234', '테스트', 1000000, NOW(), NOW()
FROM institution WHERE institution_code = 'WR';
```


## 7단계  충전 API 테스트

```bash
curl -X POST 'http://localhost:8080/api/v1/charge/execute?partyId=1' \
  -H 'Content-Type: application/json' \
  -d '{"accountId": 13, "amount": 10000, "paymentPin": "string"}'
```

성공 응답 예시

```json
{
  "isSuccess": true,
  "status": "CREATED",
  "code": "CHARGE_EXECUTED",
  "message": "충전이 완료되었습니다.",
  "result": {
    "partyId": 1,
    "chargeId": 1,
    "amount": 10000,
    "finalAmount": 9000,
    "chargedAt": "2026-05-19T15:52:43.389339"
  }
}
```

amount 는 충전 요청 액면가, finalAmount 는 10 퍼센트 할인 적용 후 실 결제 금액이다.


## distribute-cbdc.ts 설명

hangang-pay-bc/blockchain/scripts/distribute-cbdc.ts 는 한국은행 지갑에 발행된 CBDC 를 각 은행 지갑으로 전송하는 스크립트다.

CBDC 는 배포 시 한국은행 지갑에만 초기 발행된다. 각 은행이 DepositToken 을 발행하려면 해당 은행이 CBDC 를 보유하고 있어야 한다.

발행 가능량 공식

```
발행 가능량 = 은행의 CBDC 잔액 - 은행 DepositToken 총 발행량
```

은행이 CBDC 를 보유하지 않으면 발행 가능량이 0 이하가 되어 ISSUABLE_EXCEEDED 오류가 발생한다.

스크립트는 각 은행 지갑으로 1억 단위의 CBDC 를 전송하며 환경변수 CBDC_ADDRESS 로 컨트랙트 주소를 받는다.


## 오류 해결

| 오류 코드 | 원인 | 해결 |
| --- | --- | --- |
| INSTITUTION_NOT_FOUND | institution 테이블에 BoK 데이터 없음 | 3단계 SQL 재실행 |
| INSTITUTION_CONTRACT_NOT_DEPLOYED | contract_address 테이블에 배포 정보 없음 | 4단계 배포 API 재호출 |
| ISSUABLE_EXCEEDED | 은행이 CBDC 를 보유하지 않음 | 5단계 distribute-cbdc.ts 재실행 |
| BANK_ACCOUNT_NOT_FOUND | bank_account 데이터 없음 | 6단계 SQL 재실행 |
| COMMON_NOT_FOUND | wallet 데이터 없음 | 6단계 wallet INSERT 재실행 |
