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
