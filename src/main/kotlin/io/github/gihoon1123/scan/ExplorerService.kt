package io.github.gihoon1123.scan

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class ExplorerService(
    private val store: ExplorerStore,
    private val chain: ChainClient,
) {
    fun blocks(
        page: Int,
        limit: Int,
    ): Json =
        paginated(
            store.rows("SELECT * FROM blocks ORDER BY number DESC LIMIT ? OFFSET ?", limit, (page - 1) * limit).map(::blockDto),
            page,
            limit,
            store.count("blocks"),
            "블록 목록 조회 성공",
        )

    fun block(
        field: String,
        value: Any,
    ): Json {
        require(field in setOf("number", "hash"))
        val block =
            store.one("SELECT * FROM blocks WHERE \"$field\" = ?", value)
                ?: missing(if (field == "number") "블록 번호 #$value 를 찾을 수 없습니다".replace(" 를", "를") else "블록 해시 ${value}를 찾을 수 없습니다")
        return blockDto(block) +
            (
                "transactions" to
                    store.rows("SELECT * FROM transactions WHERE \"blockHash\" = ? ORDER BY nonce ASC", block["hash"]).map(::transactionDto)
            )
    }

    fun transactions(
        page: Int,
        limit: Int,
        address: String? = null,
    ): Json {
        val where = if (address == null) "" else "WHERE \"from\" = ? OR \"to\" = ?"
        val args = if (address == null) emptyArray() else arrayOf(address.lowercase(), address.lowercase())
        val rows =
            store.rows(
                "SELECT * FROM transactions $where ORDER BY \"blockNumber\" DESC, hash DESC LIMIT ? OFFSET ?",
                *args,
                limit,
                (page - 1) * limit,
            )
        return paginated(
            rows.map(::transactionDto),
            page,
            limit,
            store.count("transactions", where, *args),
            if (address ==
                null
            ) {
                "트랜잭션 목록 조회 성공"
            } else {
                "지갑 ${address.take(10)}...의 트랜잭션 목록 조회 성공"
            },
        )
    }

    fun transaction(hash: String): Json {
        val tx = store.one("SELECT * FROM transactions WHERE hash = ?", hash)
        if (tx != null) {
            val receipt = store.one("SELECT * FROM transaction_receipts WHERE \"transactionHash\" = ?", hash)
            return transactionDto(tx) + if (receipt != null) mapOf("logs" to (receipt["logs"] ?: emptyList<Any>())) else emptyMap()
        }
        return try {
            val remote = chain.request("/transaction/$hash").obj()
            val receipt = chain.receipt(hash)
            val timestamp = quantity(remote["timestamp"]).toLong()
            val value = quantity(remote["value"]).toString()
            val result =
                linkedMapOf<String, Any?>(
                    "hash" to remote["hash"],
                    "blockHash" to remote.text("blockHash"),
                    "blockNumber" to if (remote["blockNumber"] != null) quantity(remote["blockNumber"]).toString() else "",
                    "from" to remote["from"],
                    "to" to remote["to"],
                    "value" to display(value),
                    "valueWei" to value,
                    "nonce" to quantity(remote["nonce"]).toInt(),
                    "timestamp" to (timestamp * 1000).toString(),
                    "createdAt" to iso(Instant.ofEpochSecond(timestamp)),
                )
            if (receipt != null) {
                result["status"] = quantity(receipt["status"]).toInt()
                result["gasUsed"] = quantity(receipt["gasUsed"]).toString()
                result["cumulativeGasUsed"] = quantity(receipt["cumulativeGasUsed"]).toString()
                if (receipt["contractAddress"] != null) result["contractAddress"] = receipt["contractAddress"]
                result["logs"] = receipt["logs"] ?: emptyList<Any>()
            }
            result
        } catch (_: ChainFailure) {
            missing("Transaction $hash not found")
        }
    }

    fun account(address: String): Json {
        val normalized = normalizeAddress(address)
        val value = chain.request("/account/$normalized").obj()
        val wei = quantity(value["balance"]).toString()
        return mapOf(
            "address" to normalized,
            "balance" to display(wei),
            "balanceWei" to wei,
            "nonce" to quantity(value["nonce"]).toInt(),
            "txCount" to store.count("transactions", "WHERE \"from\" = ? OR \"to\" = ?", normalized, normalized),
        )
    }

    fun wallet(): Json {
        val value = chain.request("/account/create-wallet", emptyMap<String, Any>()).obj()
        val wei = quantity(value["balance"]).toString()
        return value.filterKeys { it in setOf("privateKey", "publicKey", "address", "nonce") } +
            mapOf("balanceWei" to wei, "balance" to display(wei))
    }

    fun tokenBalances(
        address: String,
        page: Int,
        limit: Int,
    ): Json {
        val a = address.lowercase()
        val rows =
            store.rows(
                """SELECT LOWER("tokenAddress") AS "tokenAddress", SUM(CASE WHEN "to" = ? THEN value WHEN "from" = ? THEN -value ELSE 0 END) AS balance FROM token_transfers WHERE "to" = ? OR "from" = ? GROUP BY LOWER("tokenAddress") HAVING SUM(CASE WHEN "to" = ? THEN value WHEN "from" = ? THEN -value ELSE 0 END) <> 0 ORDER BY LOWER("tokenAddress") ASC LIMIT ? OFFSET ?""",
                a,
                a,
                a,
                a,
                a,
                a,
                limit,
                (page - 1) * limit,
            )
        return mapOf(
            "items" to
                rows.map { row ->
                    val token = store.one("SELECT * FROM tokens WHERE address = ?", row["tokenAddress"])
                    val decimals = (token?.get("decimals") as? Number)?.toInt()
                    mapOf(
                        "tokenAddress" to row["tokenAddress"],
                        "name" to token?.get("name"),
                        "symbol" to token?.get("symbol"),
                        "decimals" to decimals,
                        "balance" to display(row["balance"], decimals ?: 18),
                        "balanceSmallestUnit" to row["balance"],
                    )
                },
        )
    }

    fun transfers(
        address: String,
        token: String?,
        page: Int,
        limit: Int,
    ): Json {
        val a = address.lowercase()
        val where = "WHERE (\"from\" = ? OR \"to\" = ?)" + if (token != null) " AND \"tokenAddress\" = ?" else ""
        val args = if (token == null) arrayOf(a, a) else arrayOf(a, a, token.lowercase())
        val rows =
            store.rows(
                "SELECT * FROM token_transfers $where ORDER BY \"blockNumber\" DESC, \"logIndex\" DESC LIMIT ? OFFSET ?",
                *args,
                limit,
                (page - 1) * limit,
            )
        return mapOf(
            "items" to
                rows.map { row ->
                    val metadata = store.one("SELECT decimals FROM tokens WHERE address = ?", row["tokenAddress"])
                    row.filterKeys {
                        it in
                            setOf(
                                "tokenAddress",
                                "from",
                                "to",
                                "blockNumber",
                                "transactionHash",
                                "logIndex",
                                "timestamp",
                            )
                    } +
                        mapOf(
                            "valueSmallestUnit" to row["value"],
                            "value" to display(row["value"], (metadata?.get("decimals") as? Number)?.toInt() ?: 18),
                            "direction" to
                                if (row["from"] == a && row["to"] == a) {
                                    "self"
                                } else if (row["to"] == a) {
                                    "in"
                                } else {
                                    "out"
                                },
                        )
                },
            "totalCount" to store.count("token_transfers", where, *args),
        )
    }

    fun contracts(
        page: Int,
        limit: Int,
        deployer: String? = null,
    ): Json {
        val where = if (deployer == null) "" else "WHERE LOWER(deployer) = ?"
        val args = if (deployer == null) emptyArray() else arrayOf(normalizeAddress(deployer))
        return paginated(
            store
                .rows(
                    "SELECT * FROM contracts $where ORDER BY \"blockNumber\" DESC, \"createdAt\" DESC LIMIT ? OFFSET ?",
                    *args,
                    limit,
                    (page - 1) * limit,
                ).map(::contractDto),
            page,
            limit,
            store.count("contracts", where, *args),
            if (deployer == null) "컨트랙트 목록 조회 성공" else "배포한 컨트랙트 목록 조회 성공",
        )
    }

    fun contract(address: String): Json = contractDto(contractEntity(address))

    fun contractEntity(address: String): Json = store.one("SELECT * FROM contracts WHERE address = ?", address) ?: missing("Contract not found: $address")

    fun updateContract(
        address: String,
        data: Json,
    ): Json {
        contractEntity(address)
        store.updateContract(address, data)
        return contract(address)
    }

    private fun blockDto(row: Json): Json = row.filterKeys { it !in setOf("id", "raw") }

    private fun contractDto(row: Json): Json = row.filterKeys { it != "id" } + ("status" to if (row.text("bytecode").let { it.isNotBlank() && it != "0x" }) 1 else 0)

    private fun transactionDto(row: Json): Json {
        val receipt = store.one("SELECT * FROM transaction_receipts WHERE \"transactionHash\" = ?", row["hash"])
        return row.filterKeys { it !in setOf("id", "raw", "value") } + mapOf("value" to display(row["value"]), "valueWei" to row["value"]) +
            (receipt?.filterKeys { it in setOf("status", "gasUsed", "cumulativeGasUsed", "contractAddress") } ?: emptyMap())
    }
}

fun missing(message: String): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, message)

fun badRequest(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)

fun normalizeAddress(address: String): String {
    if (!address.trim().matches(Regex("0x[0-9a-fA-F]{40}"))) badRequest("유효하지 않은 지갑 주소 형식입니다: $address")
    return address.trim().lowercase()
}
