# hangang-pay-bc

Blockchain module for Hangang Pay — a CBDC-based local currency payment infrastructure simulation, modeled after Bank of Korea's Project Hangang CBDC pilot.

## Overview

Four institutions participate, each owning one Besu node and one EOA account:

| Institution | Role | Token |
|---|---|---|
| Bank of Korea (BoK) | CBDC issuer | CBDC (ERC-20) |
| Woori Bank | Commercial bank | DepositToken (ERC-20) |
| Shinhan Bank | Commercial bank | DepositToken (ERC-20) |
| Hana Bank | Commercial bank | DepositToken (ERC-20) |

Each institution signs transactions directly with its own EOA. Gas price is 0 (private network). User private keys are stored in the server DB (custodial wallet).

## Directory Structure

```
hangang-pay-bc/
├── blockchain/     # Hardhat project — contract source, compile, deploy scripts
│   ├── contracts/  # Solidity source files
│   ├── scripts/    # Deploy scripts
│   └── artifacts/  # Compile output (ABI + bytecode) — gitignored except tracked files
└── network/        # Hyperledger Besu local 4-node Docker Compose
    ├── genesis.json
    ├── docker-compose.yml
    ├── node-{1..4}/data/   # Node key pairs and chain data
    └── scripts/            # Utility shell scripts
```

`blockchain/` and `network/` are independent. Contracts are compiled in `blockchain/` and deployed to the network running in `network/`.

## Network

- Client: Hyperledger Besu 26.4.0
- Consensus: QBFT (blockPeriod: 2s, requestTimeout: 4s)
- Chain ID: 1337
- EVM: London
- Gas price: 0 (`zeroBaseFee: true` in genesis)

Node port mapping (host → container):

| Node | Institution | HTTP RPC | WebSocket | P2P |
|---|---|---|---|---|
| node1 | Bank of Korea (BoK) | 8545 | 8546 | 30303 |
| node2 | Woori Bank | 8547 | 8548 | 30304 |
| node3 | Shinhan Bank | 8549 | 8550 | 30305 |
| node4 | Hana Bank | 8551 | 8552 | 30306 |

node1 is the bootnode (`172.16.239.11:30303`). Nodes 2–4 connect to it on startup.

## Smart Contracts

There are two contracts. `contracts/Token.sol` is a placeholder boilerplate — it is not either of these contracts.

### SettlementContract

Handles inter-bank transfers settled via CBDC.

Execution order (atomic — each step validated with `require` before proceeding):
1. Burn sender bank's DepositToken
2. `forceTransfer` CBDC between banks via BoK
3. Mint recipient bank's DepositToken

CBDC is ERC-20. DepositToken is ERC-20. Mint/burn authority is controlled via RBAC.

### LocalCurrencyContract

Manages local currency (HRC) balance and enforces merchant whitelist restriction.

- **Not ERC-20.** Uses `mapping(address => uint256) localBalance` to prevent direct token transfers.
- Merchant whitelist: `mapping(address => bool)`. Payments only allowed to whitelisted addresses.
- Banks hold mint/policy authority via RBAC.

## Tech Stack

- Solidity 0.8.28
- Hardhat 2.28.6 (TypeScript)
- OpenZeppelin Contracts 5.x
- EVM target: `london`
- Optimizer: enabled, 200 runs

## Commands

Run from `hangang-pay-bc/blockchain/`.

```bash
# Install dependencies
npm install

# Compile contracts
npx hardhat compile

# Deploy to local Besu network
npx hardhat run scripts/<deploy-script>.ts --network besu

# Copy compiled artifacts to BE resources (run from blockchain/)
./scripts/copy-artifacts.sh

# Start local Besu network (run from network/)
docker compose up -d

# Stop and reset chain data
./scripts/reset.sh
```

## Artifact Distribution

After compiling, ABI and bytecode artifacts must be copied to the BE module:

```
blockchain/artifacts/contracts/<ContractName>.sol/<ContractName>.json
    → hangang-pay-be/src/main/resources/contracts/
```

Use the provided script (run from `blockchain/`):

```bash
./scripts/copy-artifacts.sh
```

GitHub Actions automation is planned but not yet implemented. Until then, run this script manually after every `npx hardhat compile`.

## Relation to BE Database

These ERD tables are managed by the BE but directly depend on what is deployed in bc:

| Table | Relevance |
|---|---|
| `INSTITUTION` | Stores each institution's `wallet_address`, `encrypted_private_key`, `enode_url`, `rpc_endpoint` |
| `BANK_WALLET` | Institution's on-chain wallet address and balance |
| `CONTRACT_ADDRESS` | Deployed contract addresses per institution (`CBDC`, `DEPOSIT_TOKEN`, `CONTRACT`) |
| `WALLET` | Custodial wallets for users and merchants (private keys server-held) |
| `BLOCKCHAIN_TX` | Tracks tx hashes for `FUND_TRANSFER`, `PAYMENT`, `PAYMENT_CANCELLATION` |

## Design Decisions

| Decision | Choice | Reason |
|---|---|---|
| LocalCurrencyContract | Internal `mapping`, not ERC-20 | Block direct `transfer` entirely |
| Wallet custody | Server-held private keys | UX simplification; server signs all txs |
| Institution signing | Each institution's EOA signs directly | Simulates authority separation |
| Gas price | 0 | Private network; no cost |
| L2 | Not applied | Out of scope for this simulation |
| RBAC | Required for mint/burn | Enforces institutional role boundaries |

## What NOT to Do

- Do not make `LocalCurrencyContract` extend ERC-20 — direct `transfer` must be blocked by design.
- Do not add a standard `transfer` or `approve` function to `LocalCurrencyContract`.
- Do not store user private keys on-chain or in contract state.
- Do not set gas price to non-zero — the network is configured as a zero-fee private chain.
- Do not modify `network/node-{1..4}/data/key` files — these are the validator key pairs for QBFT consensus.
- Do not commit real private keys. The keys in `hardhat.config.ts` are well-known test accounts.
- Do not implement L2 or cross-chain bridging — out of scope.
