package io.github.gihoon1123.scan

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.concurrent.atomic.AtomicBoolean

@RestController
@ConditionalOnProperty(name = ["scan.role"], havingValue = "indexer")
class IndexerController(
    private val indexer: BlockIndexer,
) {
    @PostMapping("/indexer/process-block")
    @ResponseStatus(HttpStatus.CREATED)
    fun process(
        @RequestBody block: Json,
    ): Json {
        val number = quantity(block["number"]).toLong()
        return try {
            indexer.index(block)
            mapOf("success" to true, "blockNumber" to number)
        } catch (error: Exception) {
            mapOf("success" to false, "blockNumber" to number, "error" to (error.message ?: "Indexing failed"))
        }
    }
}

@Component
@ConditionalOnProperty(name = ["scan.role"], havingValue = "sync")
class ChainSyncer(
    private val store: ExplorerStore,
    private val chain: ChainClient,
    @param:Value("\${scan.indexer-url}") private val indexerUrl: String,
) {
    private val running = AtomicBoolean(false)
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 1000)
    fun sync() {
        if (!running.compareAndSet(false, true)) return
        try {
            val last = store.one("SELECT number FROM blocks ORDER BY number DESC LIMIT 1")
            val next = last?.text("number")?.toLong()?.plus(1) ?: 0
            val block = chain.block(next) ?: return
            val result = chain.requestUrl("${indexerUrl.trimEnd('/')}/indexer/process-block", block).obj()
            check(result["success"] == true) { "Indexer failed to process block: ${result["error"]}" }
        } catch (error: Exception) {
            logger.warn("Block sync failed; next tick retries", error)
        } finally {
            running.set(false)
        }
    }

    fun status(): Json =
        mapOf(
            "isProcessing" to running.get(),
            "lastSyncedBlock" to store.one("SELECT number, hash, timestamp FROM blocks ORDER BY number DESC LIMIT 1"),
            "indexerUrl" to indexerUrl,
        )
}

@Component
@ConditionalOnProperty(name = ["scan.role"], havingValue = "indexer")
class DatabaseCleanup(
    private val store: ExplorerStore,
    private val transaction: TransactionTemplate,
    @param:Value("\${scan.cleanup-enabled:false}") private val enabled: Boolean,
) {
    @Scheduled(cron = "0 0 23 * * *", zone = "Asia/Seoul")
    fun cleanup() {
        if (!enabled) return
        transaction.executeWithoutResult {
            for (table in listOf("token_transfers", "transaction_receipts", "transactions", "contracts", "tokens", "blocks")) {
                store.jdbc.execute("TRUNCATE TABLE $table CASCADE")
            }
        }
    }
}

@RestController
class HealthController(
    private val store: ExplorerStore,
) {
    @GetMapping("/health")
    fun health(): Json {
        val up = runCatching { store.jdbc.queryForObject("SELECT 1", Int::class.java) == 1 }.getOrDefault(false)
        if (!up) {
            throw org.springframework.web.server
                .ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Database unavailable")
        }
        val database = mapOf("database" to mapOf("status" to "up"))
        return mapOf("status" to "ok", "info" to database, "error" to emptyMap<String, Any>(), "details" to database)
    }
}

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException::class)
    fun status(error: org.springframework.web.server.ResponseStatusException): org.springframework.http.ResponseEntity<Json> {
        val status = error.statusCode.value()
        return org.springframework.http.ResponseEntity.status(status).body(
            mapOf(
                "message" to (error.reason ?: "Request failed"),
                "error" to (HttpStatus.resolve(status)?.reasonPhrase ?: "Error"),
                "statusCode" to status,
            ),
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalid(error: IllegalArgumentException): org.springframework.http.ResponseEntity<Json> =
        org.springframework.http.ResponseEntity.badRequest().body(
            mapOf(
                "message" to (error.message ?: "Bad Request"),
                "error" to "Bad Request",
                "statusCode" to 400,
            ),
        )

    @ExceptionHandler(ChainFailure::class)
    fun remote(error: ChainFailure): org.springframework.http.ResponseEntity<Json> =
        org.springframework.http.ResponseEntity.status(500).body(
            mapOf(
                "message" to "Internal server error",
                "statusCode" to 500,
            ),
        )
}
