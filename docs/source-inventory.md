# 원본 계약 목록

원본 커밋: `ee87505e0c15a3cafe597fcb23497cc11d89afa6`

원본은 NestJS 모노레포의 API(4000), indexer(4001), sync(4002) 3개 프로세스와 PostgreSQL을 사용한다. 엔티티는 blocks, transactions, transaction_receipts, accounts, contracts, tokens, token_transfers 7개다.

- `POST /indexer/process-block` — `apps/indexer/src/indexer.controller.ts`
- `GET /health` — `apps/indexer/src/health/health.controller.ts`
- `GET /health` — `apps/sync/src/health/health.controller.ts`
- `GET /blocks` — `apps/api/src/blocks/blocks.controller.ts`
- `GET /blocks/number/:number` — `apps/api/src/blocks/blocks.controller.ts`
- `GET /blocks/hash/:hash` — `apps/api/src/blocks/blocks.controller.ts`
- `GET /contracts` — `apps/api/src/contracts/contracts.controller.ts`
- `GET /contracts/deployer/:address` — `apps/api/src/contracts/contracts.controller.ts`
- `GET /contracts/:address` — `apps/api/src/contracts/contracts.controller.ts`
- `POST /contracts/deploy` — `apps/api/src/contracts/contracts.controller.ts`
- `PUT /contracts/:address/abi` — `apps/api/src/contracts/contracts.controller.ts`
- `POST /contracts/:address/execute` — `apps/api/src/contracts/contracts.controller.ts`
- `POST /contracts/:address/call` — `apps/api/src/contracts/contracts.controller.ts`
- `GET /health` — `apps/api/src/health/health.controller.ts`
- `GET /transactions` — `apps/api/src/transactions/transactions.controller.ts`
- `GET /transactions/address/:address` — `apps/api/src/transactions/transactions.controller.ts`
- `GET /transactions/:hash` — `apps/api/src/transactions/transactions.controller.ts`
- `GET /accounts/:address` — `apps/api/src/accounts/accounts.controller.ts`
- `GET /accounts/:address/tokens` — `apps/api/src/accounts/accounts.controller.ts`
- `GET /accounts/:address/token-transfers` — `apps/api/src/accounts/accounts.controller.ts`
- `POST /accounts/create-wallet` — `apps/api/src/accounts/accounts.controller.ts`
- `POST /accounts/transfer-native` — `apps/api/src/accounts/accounts.controller.ts`
- `POST /staking/deposit` — `apps/api/src/staking/staking.controller.ts`
- `POST /staking/set-withdrawal-address` — `apps/api/src/staking/staking.controller.ts`
- `POST /staking/request-withdrawal` — `apps/api/src/staking/staking.controller.ts`
- `GET /staking/validator/:address` — `apps/api/src/staking/staking.controller.ts`
- `GET /staking/validators` — `apps/api/src/staking/staking.controller.ts`
- `GET /staking/stats` — `apps/api/src/staking/staking.controller.ts`
- `POST /stablecoin/deposit` — `apps/api/src/stablecoin/stablecoin.controller.ts`
- `POST /stablecoin/mint` — `apps/api/src/stablecoin/stablecoin.controller.ts`
- `POST /stablecoin/redeem` — `apps/api/src/stablecoin/stablecoin.controller.ts`
- `POST /stablecoin/withdraw` — `apps/api/src/stablecoin/stablecoin.controller.ts`
- `POST /stablecoin/liquidate` — `apps/api/src/stablecoin/stablecoin.controller.ts`
- `GET /stablecoin/position/:userAddress` — `apps/api/src/stablecoin/stablecoin.controller.ts`
- `GET /stablecoin/health/:userAddress` — `apps/api/src/stablecoin/stablecoin.controller.ts`
- `GET /stablecoin/balance/:userAddress` — `apps/api/src/stablecoin/stablecoin.controller.ts`
- `POST /stablecoin/transfer-stablecoin` — `apps/api/src/stablecoin/stablecoin.controller.ts`
