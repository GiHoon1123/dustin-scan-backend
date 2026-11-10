package io.github.gihoon1123.scan

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class ChainFailure(
    val status: Int,
    message: String,
) : RuntimeException(message)

@Component
class ChainClient(
    private val mapper: ObjectMapper,
    @param:Value("\${scan.chain-url}") private val baseUrl: String,
) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    fun request(
        path: String,
        body: Any? = null,
        method: String = if (body == null) "GET" else "POST",
    ): Any? = requestUrl(baseUrl.trimEnd('/') + path, body, method)

    fun requestUrl(
        url: String,
        body: Any? = null,
        method: String = if (body == null) "GET" else "POST",
    ): Any? {
        val publisher =
            if (body == null) {
                HttpRequest.BodyPublishers.noBody()
            } else {
                HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))
            }
        val request =
            HttpRequest
                .newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(if (method == "GET") 10 else 30))
                .header("Content-Type", "application/json")
                .method(method, publisher)
                .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) throw ChainFailure(response.statusCode(), "Chain request failed (${response.statusCode()})")
        if (response.body().isBlank()) return null
        return mapper.readValue(response.body(), object : TypeReference<Any?>() {})
    }

    fun receipt(hash: String): Json? = request("/transaction/$hash/receipt")?.obj()

    fun block(number: Long): Json? =
        try {
            request("/block/number/$number")?.obj()
        } catch (error: ChainFailure) {
            if (error.status == 404) null else throw error
        }

    fun waitForTransaction(
        hash: String,
        attempts: Int = 20,
        intervalMs: Long = 3000,
    ): Json {
        repeat(attempts) { index ->
            val receipt = runCatching { receipt(hash) }.getOrNull()
            if (receipt != null && receipt["blockNumber"] != null) {
                return mapOf(
                    "hash" to hash,
                    "status" to if (quantity(receipt["status"]) == java.math.BigInteger.ZERO) "failed" else "confirmed",
                    "blockNumber" to quantity(receipt["blockNumber"]).toString(),
                    "blockHash" to receipt["blockHash"],
                )
            }
            if (index < attempts - 1) Thread.sleep(intervalMs)
        }
        return mapOf("hash" to hash, "status" to "pending")
    }
}
