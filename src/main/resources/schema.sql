CREATE TABLE IF NOT EXISTS "transactions" (
  "id" SERIAL PRIMARY KEY,
  "hash" VARCHAR NOT NULL UNIQUE,
  "blockHash" VARCHAR,
  "blockNumber" BIGINT,
  "from" VARCHAR NOT NULL,
  "to" VARCHAR NOT NULL,
  "value" NUMERIC(78,0) NOT NULL,
  "nonce" INTEGER NOT NULL,
  "timestamp" BIGINT NOT NULL,
  "raw" JSONB,
  "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS "idx_transactions_blockHash" ON "transactions" ("blockHash");
CREATE INDEX IF NOT EXISTS "idx_transactions_blockNumber" ON "transactions" ("blockNumber");
CREATE INDEX IF NOT EXISTS "idx_transactions_from" ON "transactions" ("from");
CREATE INDEX IF NOT EXISTS "idx_transactions_to" ON "transactions" ("to");

CREATE TABLE IF NOT EXISTS "contracts" (
  "id" SERIAL PRIMARY KEY,
  "address" VARCHAR NOT NULL UNIQUE,
  "deployer" VARCHAR NOT NULL,
  "transactionHash" VARCHAR NOT NULL,
  "blockNumber" BIGINT NOT NULL,
  "blockHash" VARCHAR NOT NULL,
  "bytecode" TEXT,
  "abi" JSONB,
  "sourceCode" TEXT,
  "name" VARCHAR,
  "compilerVersion" VARCHAR,
  "optimization" BOOLEAN,
  "timestamp" BIGINT NOT NULL,
  "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS "idx_contracts_deployer" ON "contracts" ("deployer");
CREATE INDEX IF NOT EXISTS "idx_contracts_transactionHash" ON "contracts" ("transactionHash");
CREATE INDEX IF NOT EXISTS "idx_contracts_blockNumber" ON "contracts" ("blockNumber");
CREATE INDEX IF NOT EXISTS "idx_contracts_blockHash" ON "contracts" ("blockHash");

CREATE TABLE IF NOT EXISTS "accounts" (
  "id" SERIAL PRIMARY KEY,
  "address" VARCHAR NOT NULL UNIQUE,
  "balance" NUMERIC(78,0) NOT NULL DEFAULT 0,
  "nonce" INTEGER NOT NULL DEFAULT 0,
  "txCount" INTEGER NOT NULL DEFAULT 0,
  "lastUpdated" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE IF NOT EXISTS "tokens" (
  "id" SERIAL PRIMARY KEY,
  "address" VARCHAR NOT NULL UNIQUE,
  "name" VARCHAR,
  "symbol" VARCHAR,
  "decimals" INTEGER,
  "type" VARCHAR NOT NULL DEFAULT 'erc20',
  "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE IF NOT EXISTS "transaction_receipts" (
  "id" SERIAL PRIMARY KEY,
  "transactionHash" VARCHAR NOT NULL UNIQUE,
  "transactionIndex" INTEGER NOT NULL,
  "blockHash" VARCHAR NOT NULL,
  "blockNumber" BIGINT NOT NULL,
  "from" VARCHAR NOT NULL,
  "to" VARCHAR NOT NULL,
  "status" INTEGER NOT NULL,
  "gasUsed" NUMERIC(78,0) NOT NULL,
  "cumulativeGasUsed" NUMERIC(78,0) NOT NULL,
  "contractAddress" VARCHAR,
  "logs" JSONB NOT NULL DEFAULT '[]'::jsonb,
  "logsBloom" VARCHAR NOT NULL
);
CREATE INDEX IF NOT EXISTS "idx_transaction_receipts_blockHash" ON "transaction_receipts" ("blockHash");
CREATE INDEX IF NOT EXISTS "idx_transaction_receipts_blockNumber" ON "transaction_receipts" ("blockNumber");
CREATE INDEX IF NOT EXISTS "idx_transaction_receipts_from" ON "transaction_receipts" ("from");
CREATE INDEX IF NOT EXISTS "idx_transaction_receipts_to" ON "transaction_receipts" ("to");

CREATE TABLE IF NOT EXISTS "token_transfers" (
  "id" SERIAL PRIMARY KEY,
  "tokenAddress" VARCHAR NOT NULL,
  "from" VARCHAR NOT NULL,
  "to" VARCHAR NOT NULL,
  "value" NUMERIC(78,0) NOT NULL,
  "blockNumber" BIGINT NOT NULL,
  "blockHash" VARCHAR NOT NULL,
  "transactionHash" VARCHAR NOT NULL,
  "logIndex" INTEGER NOT NULL,
  "timestamp" BIGINT NOT NULL,
  "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS "idx_token_transfers_tokenAddress" ON "token_transfers" ("tokenAddress");
CREATE INDEX IF NOT EXISTS "idx_token_transfers_from" ON "token_transfers" ("from");
CREATE INDEX IF NOT EXISTS "idx_token_transfers_to" ON "token_transfers" ("to");
CREATE INDEX IF NOT EXISTS "idx_token_transfers_blockNumber" ON "token_transfers" ("blockNumber");
CREATE INDEX IF NOT EXISTS "idx_token_transfers_blockHash" ON "token_transfers" ("blockHash");
CREATE INDEX IF NOT EXISTS "idx_token_transfers_transactionHash" ON "token_transfers" ("transactionHash");

CREATE TABLE IF NOT EXISTS "blocks" (
  "id" SERIAL PRIMARY KEY,
  "hash" VARCHAR NOT NULL UNIQUE,
  "number" BIGINT NOT NULL UNIQUE,
  "timestamp" BIGINT NOT NULL,
  "parentHash" VARCHAR NOT NULL,
  "proposer" VARCHAR NOT NULL,
  "transactionCount" INTEGER NOT NULL,
  "stateRoot" VARCHAR NOT NULL,
  "transactionsRoot" VARCHAR NOT NULL,
  "receiptsRoot" VARCHAR NOT NULL,
  "raw" JSONB,
  "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

