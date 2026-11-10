package io.github.gihoon1123.scan

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.ObjectMapper
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.ConcurrentHashMap

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "SCAN_TEST_DATABASE_URL", matches = ".+")
class PostgresIntegrationTest {
    @Autowired lateinit var store: ExplorerStore

    @Autowired lateinit var indexer: BlockIndexer

    @Autowired lateinit var mapper: ObjectMapper

    @LocalServerPort var port: Int = 0

    @BeforeEach
    fun clear() {
        store.jdbc.execute("TRUNCATE blocks, transactions, transaction_receipts, contracts, tokens, token_transfers, accounts RESTART IDENTITY CASCADE")
        replies.clear()
    }

    @Test
    fun `block transaction receipt contract transfer save atomically and replay does not duplicate`() {
        val receipt = receipt()
        replies["/transaction/tx1/receipt"] = receipt
        replies["/contract/contract1/bytecode"] = mapOf("bytecode" to "0x6000")
        indexer.index(block())
        indexer.index(block())
        for (table in listOf("blocks", "transactions", "transaction_receipts", "contracts", "tokens", "token_transfers")) assertEquals(1L, store.count(table), table)
        val response = get("/blocks/number/1")
        assertEquals(true, response["success"])
        val data = response["data"].obj()
        assertEquals("1", data["number"])
        val tx = data["transactions"].objects().single()
        assertEquals("1000000000000000001", tx["valueWei"])
        assertEquals("1.0", tx["value"])
        assertEquals(1, tx["status"])
        val contract = get("/contracts/contract1")["data"].obj()
        assertEquals(1, contract["status"])
    }

    @Test
    fun `receipt parsing failure rolls back parent block and transactions`() {
        replies["/transaction/tx1/receipt"] = receipt() + ("gasUsed" to "not-hex")
        assertThrows(NumberFormatException::class.java) { indexer.index(block()) }
        assertEquals(0L, store.count("blocks"))
        assertEquals(0L, store.count("transactions"))
        assertEquals(0L, store.count("token_transfers"))
    }

    @Test
    fun `partial preexisting block is repaired without duplicate parent`() {
        store.insert("blocks", mapOf("hash" to "b1", "number" to 1L, "timestamp" to 100L, "parentHash" to "b0", "proposer" to "p", "transactionCount" to 1, "stateRoot" to "s", "transactionsRoot" to "t", "receiptsRoot" to "r"))
        replies["/transaction/tx1/receipt"] = receipt()
        replies["/contract/contract1/bytecode"] = mapOf("bytecode" to "0x6000")
        indexer.index(block())
        assertEquals(1L, store.count("blocks"))
        assertEquals(1L, store.count("transactions"))
        assertEquals(1L, store.count("transaction_receipts"))
    }

    @Test
    fun `API account quantities token direction and receipt logs preserve wire types`() {
        replies["/transaction/tx1/receipt"] = receipt()
        replies["/contract/contract1/bytecode"] = mapOf("bytecode" to "0x6000")
        replies["/account/$RECIPIENT"] = mapOf("balance" to "0xde0b6b3a7640001", "nonce" to "0x2")
        indexer.index(block())
        val account = get("/accounts/$RECIPIENT")["data"].obj()
        assertEquals("1000000000000000001", account["balanceWei"])
        assertEquals(2, account["nonce"])
        val transfers = get("/accounts/$RECIPIENT/token-transfers")["data"].obj()["items"].objects()
        assertEquals("in", transfers.single()["direction"])
        assertEquals("1000000000000000000", transfers.single()["valueSmallestUnit"])
        val detail = get("/transactions/tx1")["data"].obj()
        assertEquals(1, detail["logs"].objects().size)
        assertFalse(detail.containsKey("raw"))
    }

    @Test
    fun `core fallback transaction uses milliseconds and pending omits receipt fields`() {
        replies["/transaction/pending"] = mapOf("hash" to "pending", "from" to SENDER, "to" to RECIPIENT, "value" to "0x1", "nonce" to "0x0", "timestamp" to "0x64")
        val data = get("/transactions/pending")["data"].obj()
        assertEquals("100000", data["timestamp"])
        assertEquals("1970-01-01T00:01:40.000Z", data["createdAt"])
        assertFalse(data.containsKey("status"))
    }

    @Test
    fun `API stablecoin mixed envelope contracts and staking decimal conversions are preserved`() {
        replies["/stablecoin/position/a"] = mapOf("collateralAmount" to "0xde0b6b3a7640000", "debtAmount" to "0x0", "collateralRatio" to "0x96")
        val position = get("/stablecoin/position/a")
        assertEquals("1.0", position["collateralAmount"])
        assertFalse(position.containsKey("success"))
        replies["/staking/validator/a"] = mapOf("validatorAddress" to "a", "status" to "active_ongoing", "withdrawalAddress" to "w", "stakedAmount" to "0x1bc16d674ec800000", "totalRewards" to "0x0", "slashedAmount" to "0x", "activatedAt" to "0x64", "exitRequestedAt" to "0x0")
        val validator = get("/staking/validator/a")["data"].obj()
        assertEquals("32.0", validator["stakedAmount"])
        assertEquals("100", validator["activatedAt"])
    }

    @Test
    fun `health checks actual database and missing block returns Nest error shape`() {
        assertEquals("ok", get("/health")["status"])
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/blocks/number/999")).GET().build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(404, response.statusCode())
        assertTrue(response.body().contains("statusCode"))
    }

    private fun get(path: String): Json {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port$path")).GET().build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode(), response.body())
        return mapper.readValue(response.body(), Map::class.java).obj()
    }

    companion object {
        private const val SENDER = "0x1111111111111111111111111111111111111111"
        private const val RECIPIENT = "0x2222222222222222222222222222222222222222"
        private val replies = ConcurrentHashMap<String, Json>()
        private val server =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/") { exchange ->
                    val body =
                        tools.jackson.databind.json.JsonMapper
                            .builder()
                            .build()
                            .writeValueAsBytes(replies[exchange.requestURI.path])
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                start()
            }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { System.getenv("SCAN_TEST_DATABASE_URL") }
            registry.add("spring.datasource.username") { "postgres" }
            registry.add("spring.datasource.password") { "postgres" }
            registry.add("spring.sql.init.mode") { "always" }
            registry.add("scan.chain-url") { "http://127.0.0.1:${server.address.port}" }
        }

        @JvmStatic @AfterAll
        fun stop() {
            server.stop(0)
        }

        private fun block(): Json = mapOf("hash" to "b1", "number" to "0x1", "timestamp" to "0x64", "parentHash" to "b0", "proposer" to "p", "transactionCount" to "0x1", "stateRoot" to "s", "transactionsRoot" to "t", "receiptsRoot" to "r", "transactions" to listOf(mapOf("hash" to "tx1", "from" to SENDER, "to" to RECIPIENT, "value" to "0xde0b6b3a7640001", "nonce" to "0x0", "timestamp" to "0x64")))

        private fun receipt(): Json = mapOf("transactionHash" to "tx1", "transactionIndex" to "0x0", "blockHash" to "b1", "blockNumber" to "0x1", "from" to SENDER, "to" to RECIPIENT, "status" to "0x1", "gasUsed" to "0x5208", "cumulativeGasUsed" to "0x5208", "contractAddress" to "contract1", "logsBloom" to "0x00", "logs" to listOf(mapOf("address" to "token1", "topics" to listOf(BlockIndexer.TRANSFER_TOPIC, "0x" + "0".repeat(24) + SENDER.drop(2), "0x" + "0".repeat(24) + RECIPIENT.drop(2)), "data" to "0xde0b6b3a7640000", "logIndex" to 0)))
    }
}
