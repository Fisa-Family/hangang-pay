# 결제 VU 부하 테스트: rate limit OFF/ON p99 비교

/ be + bank + bc 로컬 풀스택, k6 부하. 2026-06-15, `stressTest` 브랜치.

## 목적

결제 `execute` 경로에 부하를 걸어 **rate limit을 풀었을 때**와 **걸었을 때**의 p99·throughput·차단율을
비교한다. rate limit을 설정으로 ON/OFF 가능하게 만들고(`payment.rate-limit.*`), 동일 k6 스크립트로 두 번 측정했다.

## 측정 대상의 성격 (중요)

결제 API의 p99는 **블록체인 블록타임(2s)에 지배되지 않는다.** bank `POST /transactions/payment`는
DB 잔액 차감 + 아웃박스 저장 + 동기 ethCall(가맹점 whitelist 조회) 1회 후 즉시 응답하고, 온체인 확정은
아웃박스(5s)+RabbitMQ+Besu로 **비동기** 처리된다. 따라서 측정 p99 = `BE rate-limit(Redis Lua) + bank 동기 경로`.

## 환경

- BE 8080 / bank 8081 / Besu 4-node(QBFT 2s) / MySQL / Redis / RabbitMQ — 모두 단일 머신 로컬
- 시드: `loadtest` 프로필 `LoadTestDataInitializer`로 유저 300명 + 지갑 + 잔액(`bank_wallet.balance` 1e9 직접 충전)
- 결제 수취 가맹점: 기준 시드 가맹점 1개 공유 (`merchantPartyId=2`)
- rate limit 토글: `application-loadtest-off.yaml`(enabled=false) / `application-loadtest-on.yaml`(튜닝)

## rate limit 설정값

| 한도 | 종류 | OFF | ON(튜닝) | 운영기본 |
|---|---|---|---|---|
| intent | 사용자별 토큰버킷 | 끔 | cap 1000 / 6000·min⁻¹ (비병목) | cap 10 / 1·min⁻¹ |
| execute | 사용자별 슬라이딩윈도우 | 끔 | 60 / 1min (비병목) | 5 / 10min |
| **bank-outbound** | **전역 토큰버킷** | 끔 | **cap 40 / 20·s⁻¹ (셰딩 지점)** | cap 50 / 10·s⁻¹ |

## 결과

### A. closed-loop (ramping-vus, 300 VU, OFF)

| 지표 | 값 |
|---|---|
| execute 처리량 | ~47 req/s |
| execute p99 (k6 client) | **5.71s** |
| execute p99 (actuator 히스토그램, status=200) | **5.73s** ← 교차검증 일치 |
| 429 / 실패 | 0 |

→ rate limit 없이 300 VU만으로 이미 **다운스트림 포화**. p99 5.7s.

### B. open-loop (constant-arrival-rate, **70 req/s 고정 주입**, OFF vs ON) — 동일 조건 비교

| 지표 | OFF (limit 끔) | ON (전역 20/s) |
|---|---|---|
| 2xx 처리량 | ~32 req/s | ~19 req/s |
| 429 (셰딩) | 0 | **~16 req/s** |
| 2xx latency avg / p95 | 5.65s / 7.85s | 5.49s / 8.19s |
| 429 latency avg / p95 | — | **4.97s / 7.32s** |
| dropped (k6 VU 고갈) | 2995 | 2822 |

### C. 서버측 자원 메트릭 (actuator/Micrometer, OFF-final 런)

| 메트릭 | 값 | 해석 |
|---|---|---|
| `hikaricp_connections_max` | **10** | DB 커넥션 풀 기본 10개 — 동시 결제 10건만 DB 작업 가능 |
| 커넥션 획득 평균 대기 | **2.6s** | acquire 7098회 / 누적 18,439s → 스레드가 커넥션 얻는 데만 평균 2.6초 |
| `hikaricp_connections_pending` / `timeout_total` | 0 / 0 | 종료 후 idle 값. 런 중엔 pending 다수였을 것 |
| execute p99 (히스토그램, status=200) | 5.73s | k6 client p99와 일치(교차검증) |

→ 커넥션 획득 대기 2.6초가 측정 지연(~5s)의 큰 축. **HikariCP 풀(10) + 단일 가맹점 row-lock**이 직렬화의 뿌리.

미수집: Grafana/Prometheus 대시보드·Alloy·Sentry(로컬 미기동), 런 중 자원 메트릭 시계열, `tomcat_threads_*`(이 빌드 미노출).

## 핵심 결론

**차단 자체는 잘 됐다.** ON에서 초과분이 초당 16건쯤 429로 잘려 나갔고, 전역 버킷이 은행 호출을 초당 20건 선에서
막아줬다. 동시 버스트 150건을 따로 쏴 보니 107건은 통과, 43건은 429였고 Redis 토큰도 0까지 떨어졌다.

**그런데 p99는 전혀 안 줄었다.** 같은 초당 70건 부하에서 통과한 요청(2xx)의 지연이 OFF나 ON이나 비슷했다
(p95 7.85초 vs 8.19초). 더 이상한 건 **429조차 응답에 5초쯤 걸렸다는 점**이다. 곧바로 거절돼야 할 요청이 5초를
기다린 셈이다.

원인을 따라가 보면 병목은 rate limiter가 아니었다. 서버측 메트릭(위 C)이 가리키는 범인은 둘이다. 하나는
**DB 커넥션 풀(HikariCP, 기본 10개)** — 동시 결제가 아무리 많아도 10건만 DB를 잡고, 나머지는 커넥션을 얻으려
평균 2.6초를 기다렸다. 다른 하나는 가맹점이 하나뿐이라 생기는 `bank_wallet` row-lock이다. 결제가 전부 같은
가맹점 지갑 row를 `FOR UPDATE`로 잠그니, 그 10개 커넥션마저 같은 row에서 한 줄로 직렬화되고, 그 바람에
시스템이 초당 45건쯤에서 천장을 친다.
느린 요청이 스레드를 몇 초씩 붙잡고 있으면 뒤따라온 요청은 스레드를 못 얻고 큐에서 기다린다. 그런데
rate-limit 검사는 핸들러에 들어온 뒤, 즉 스레드를 이미 잡은 다음에 돌기 때문에 429로 잘릴 요청도 이 큐 대기
5초를 먼저 치르고 만다.

**정리하면**, 핸들러 안에서 도는 rate limit은 백엔드를 지켜주긴 해도(셰딩), 스레드 풀이 병목인 상황에서는
지연을 줄여주지 못한다. 지연까지 잡으려면 셰딩을 더 앞단(게이트웨이나 필터)으로 끌어올리든지, 자원 병목
자체를 풀어야 한다. 후자는 HikariCP 풀을 키우고, 단일 가맹점 row-lock을 가맹점 지갑 샤딩·원장 분리·비동기
처리로 푸는 쪽이다. (단, 풀만 키우면 row-lock 직렬화가 다음 천장이 되므로 둘을 같이 봐야 한다.)

## 재현 방법

```bash
# 인프라
docker run -d --name hp-rabbitmq -p 5672:5672 rabbitmq:3-management
docker exec hp-rabbitmq rabbitmq-plugins enable rabbitmq_sharding   # x-modulus-hash exchange
redis-server --daemonize yes
mysql -uroot -p1234 -e "CREATE DATABASE hangang_pay; CREATE DATABASE hangang_pay_bank;"
# bc
cd hangang-pay-bc/network && docker compose up -d
cd ../blockchain && npm install --cache /tmp/npmcache && npx hardhat compile
# bank 먼저 기동(배포 스크립트가 bank로 주소 POST) → 배포
cd ../../hangang-pay-bank && ./gradlew bootRun --args='--spring.profiles.active=local'
cd ../hangang-pay-bc/blockchain && npx hardhat run scripts/deploy-uups.ts --network besu
# BE 시드(최초 1회, create-drop) — 유저당 ~2s(온체인 지갑+민트)
cd ../../hangang-pay-be
LOADTEST_USER_COUNT=300 LOADTEST_MINT_AMOUNT=15000 \
  ./gradlew bootRun --args='--spring.profiles.active=local,loadtest,loadtest-off'
# 결제 잔액은 bank_wallet.balance(DB)에서 차감되므로 직접 충전
mysql -uroot -p1234 hangang_pay_bank -e "UPDATE bank_wallet SET balance=1000000000 WHERE balance<1000000000;"
# k6 (open model)
RATE=70 PRE_VUS=400 HOLD=90s k6 run k6/payment-loadtest.js          # OFF
# ON 전환: BE를 kill -9 (create-drop 테이블 보존) 후 ddl-auto=none 재기동 → 재시드 없이 재사용
LOADTEST_USER_COUNT=300 ./gradlew bootRun \
  --args='--spring.profiles.active=local,loadtest,loadtest-on --spring.jpa.hibernate.ddl-auto=none'
RATE=70 PRE_VUS=400 HOLD=90s k6 run k6/payment-loadtest.js          # ON
```

## 함정 메모 (다음 사람용)

- `LOADTEST_MINT_AMOUNT × N` 합계가 컨트랙트 `MAX_TOTAL_ISSUANCE`(1천만 토큰)를 넘으면 `IssuanceLimitExceeded` 리버트.
  on-chain `totalIssued`는 besu에 누적되어 BE 재시작마다 합산됨(reset하려면 besu 재기동+재배포).
- 결제 가용 잔액은 on-chain이 아니라 **bank `bank_wallet.balance`(DB)**. `localMint`는 on-chain만 올리므로
  부하용으로는 DB 잔액을 직접 채워야 결제가 SUCCESS.
- intent는 `(userId+merchant+amount)` 30초 dedup. k6에서 amount를 iteration마다 고유하게 안 하면 같은
  transactionUuid로 수렴 → 409가 rate limiter 이전에 단락되어 셰딩이 안 보임. (스크립트에서 amount 고유화 적용)
