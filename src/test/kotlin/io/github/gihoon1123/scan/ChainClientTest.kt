package io.github.gihoon1123.scan

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress

class ChainClientTest {
    @Test
    fun `404 block means not yet produced while other failures propagate`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/block/number/1") {
            it.sendResponseHeaders(404, -1)
            it.close()
        }
        server.createContext("/block/number/2") {
            it.sendResponseHeaders(500, -1)
            it.close()
        }
        server.start()
        try {
            val client = ChainClient(JsonMapper.builder().build(), "http://127.0.0.1:${server.address.port}")
            assertNull(client.block(1))
            assertEquals(500, assertThrows(ChainFailure::class.java) { client.block(2) }.status)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `receipt wait reports failed confirmed and pending without inventing success`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/transaction/") {
            val value =
                when {
                    it.requestURI.path.contains("pending") -> "null"
                    it.requestURI.path.contains("failed") -> """{"blockNumber":"0xa","blockHash":"h","status":"0x0"}"""
                    else -> """{"blockNumber":"0xa","blockHash":"h","status":"0x1"}"""
                }.toByteArray()
            it.sendResponseHeaders(200, value.size.toLong())
            it.responseBody.use { out -> out.write(value) }
        }
        server.start()
        try {
            val client = ChainClient(JsonMapper.builder().build(), "http://127.0.0.1:${server.address.port}")
            assertEquals("pending", client.waitForTransaction("pending", 2, 0)["status"])
            assertEquals("failed", client.waitForTransaction("failed", 1, 0)["status"])
            assertEquals("10", client.waitForTransaction("success", 1, 0)["blockNumber"])
        } finally {
            server.stop(0)
        }
    }
}
