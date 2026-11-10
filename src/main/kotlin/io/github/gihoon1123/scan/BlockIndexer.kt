package io.github.gihoon1123.scan

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class BlockIndexer(
    private val store: ExplorerStore,
    private val chain: ChainClient,
) {
    @Transactional
    fun index(block: Json) {
        val hash = block.text("hash")
        require(hash.isNotBlank()) { "Block hash is required" }
        val txs = block["transactions"].objects()
        val receipts = txs.map { chain.receipt(it.text("hash")) }
        val existing = store.one("SELECT * FROM blocks WHERE hash = ?", hash)
        if (existing != null &&
            txs.indices.all { index ->
                store.one("SELECT hash FROM transactions WHERE hash = ?", txs[index].text("hash")) != null &&
                    (
                        receipts[index] == null ||
                            store.one("SELECT id FROM transaction_receipts WHERE \"transactionHash\" = ?", txs[index].text("hash")) != null
                    )
            }
        ) {
            return
        }
        val number = quantity(block["number"]).toLong()
        val timestamp = quantity(block["timestamp"]).toLong()
        store.insert(
            "blocks",
            mapOf(
                "hash" to hash,
                "number" to number,
                "timestamp" to timestamp,
                "parentHash" to block.text("parentHash"),
                "proposer" to block.text("proposer"),
                "transactionCount" to quantity(block["transactionCount"]).toInt(),
                "stateRoot" to block.text("stateRoot"),
                "transactionsRoot" to block.text("transactionsRoot"),
                "receiptsRoot" to block.text("receiptsRoot"),
                "raw" to block,
            ),
            "hash",
        )
        txs.forEachIndexed { index, tx ->
            store.insert(
                "transactions",
                mapOf(
                    "hash" to tx.text("hash"),
                    "blockHash" to hash,
                    "blockNumber" to number,
                    "from" to tx.text("from"),
                    "to" to tx.text("to"),
                    "value" to BigDecimal(quantity(tx["value"])),
                    "nonce" to quantity(tx["nonce"]).toInt(),
                    "timestamp" to quantity(tx["timestamp"]).toLong(),
                    "raw" to tx,
                ),
                "hash",
            )
            receipts[index]?.let { receipt -> saveReceipt(receipt, block, number, timestamp) }
        }
    }

    private fun saveReceipt(
        receipt: Json,
        block: Json,
        number: Long,
        timestamp: Long,
    ) {
        val hash = receipt.text("transactionHash")
        val address = receipt.text("contractAddress")
        store.insert(
            "transaction_receipts",
            mapOf(
                "transactionHash" to hash,
                "transactionIndex" to quantity(receipt["transactionIndex"]).toInt(),
                "blockHash" to receipt.text("blockHash"),
                "blockNumber" to quantity(receipt["blockNumber"]).toLong(),
                "from" to receipt.text("from"),
                "to" to receipt.text("to"),
                "status" to quantity(receipt["status"]).toInt(),
                "gasUsed" to BigDecimal(quantity(receipt["gasUsed"])),
                "cumulativeGasUsed" to BigDecimal(quantity(receipt["cumulativeGasUsed"])),
                "contractAddress" to receipt["contractAddress"],
                "logs" to (receipt["logs"] ?: emptyList<Any>()),
                "logsBloom" to receipt.text("logsBloom"),
            ),
            "transactionHash",
        )
        receipt["logs"].objects().forEachIndexed { index, log ->
            val topics = (log["topics"] as? List<*>) ?: return@forEachIndexed
            if (topics.size < 3 || topics[0].toString().lowercase() != TRANSFER_TOPIC) return@forEachIndexed
            val logIndex = (log["logIndex"] as? Number)?.toInt() ?: index
            val token = log.text("address").lowercase()
            store.insert(
                "tokens",
                mapOf("address" to token, "name" to null, "symbol" to null, "decimals" to null, "type" to "erc20"),
                "address",
            )
            if (store.one("SELECT id FROM token_transfers WHERE \"transactionHash\" = ? AND \"logIndex\" = ?", hash, logIndex) == null) {
                store.insert(
                    "token_transfers",
                    mapOf(
                        "tokenAddress" to token,
                        "from" to topicAddress(topics[1]),
                        "to" to topicAddress(topics[2]),
                        "value" to BigDecimal(quantity(log["data"])),
                        "blockNumber" to number,
                        "blockHash" to receipt.text("blockHash"),
                        "transactionHash" to hash,
                        "logIndex" to logIndex,
                        "timestamp" to timestamp,
                    ),
                )
            }
        }
        if (address.isNotEmpty() && store.one("SELECT id FROM contracts WHERE address = ?", address) == null) {
            val bytecode = runCatching { chain.request("/contract/$address/bytecode").obj().text("bytecode", "0x") }.getOrDefault("0x")
            store.insert(
                "contracts",
                mapOf(
                    "address" to address,
                    "deployer" to receipt.text("from"),
                    "transactionHash" to hash,
                    "blockNumber" to number,
                    "blockHash" to block.text("hash"),
                    "bytecode" to bytecode,
                    "abi" to null,
                    "sourceCode" to null,
                    "name" to null,
                    "compilerVersion" to null,
                    "optimization" to null,
                    "timestamp" to timestamp,
                ),
                "address",
            )
        }
    }

    companion object {
        const val TRANSFER_TOPIC = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"

        fun topicAddress(topic: Any?): String =
            "0x" +
                topic
                    .toString()
                    .removePrefix("0x")
                    .takeLast(40)
                    .lowercase()
    }
}
