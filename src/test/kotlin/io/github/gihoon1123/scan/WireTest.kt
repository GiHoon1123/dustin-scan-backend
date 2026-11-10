package io.github.gihoon1123.scan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigInteger

class WireTest {
    @Test
    fun `quantities preserve integers beyond JavaScript precision`() {
        assertEquals(BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129639935"), quantity("0x" + "f".repeat(64)))
        assertEquals(BigInteger.ZERO, quantity("0x"))
        assertEquals(BigInteger("21000"), quantity("0x5208"))
    }

    @Test
    fun `DSTN display retains original truncation and trailing zeros`() {
        assertEquals("1.0", display("1000000000000000000"))
        assertEquals("0.5", display("500000000000000000"))
        assertEquals("1.2345", display("1234567890000000000"))
        assertEquals("0.0", display("1"))
        assertEquals("1.2345", display("1234567", 6))
        assertEquals("1000.0", display("1000", 0))
    }

    @Test
    fun `input conversion preserves eighteen decimal truncation`() {
        assertEquals(BigInteger("1500000000000000000"), toWei("1.5"))
        assertEquals(BigInteger.ONE, toWei("0.0000000000000000019"))
        assertEquals(BigInteger("500000000000000000"), toWei(".5"))
    }

    @Test
    fun `pagination wire shape matches original`() {
        val response = paginated(emptyList(), 2, 20, 41, "목록")
        val pagination = response["data"].obj()["pagination"].obj()
        assertEquals(3L, pagination["totalPages"])
        assertEquals(true, pagination["hasNext"])
        assertEquals(true, pagination["hasPrevious"])
        assertTrue(response.text("timestamp").matches(Regex(".*\\.\\d{3}Z")))
    }

    @Test
    fun `address normalization rejects malformed address`() {
        assertEquals("0x" + "ab".repeat(20), normalizeAddress("0x" + "AB".repeat(20)))
        assertThrows(org.springframework.web.server.ResponseStatusException::class.java) { normalizeAddress("bad") }
    }

    @Test
    fun `ERC20 topic extraction uses last twenty bytes`() {
        assertEquals("0x" + "ab".repeat(20), BlockIndexer.topicAddress("0x" + "0".repeat(24) + "AB".repeat(20)))
    }

    @Test
    fun `ABI transfer input and uint output match Ethereum encoding`() {
        val entry = mapOf("name" to "transfer", "inputs" to listOf(mapOf("type" to "address"), mapOf("type" to "uint256")), "outputs" to listOf(mapOf("type" to "bool")))
        assertEquals("0xa9059cbb" + "0".repeat(24) + "11".repeat(20) + "0".repeat(63) + "1", ContractService.encode(entry, listOf("0x" + "11".repeat(20), "1")))
        assertEquals(true, ContractService.decode(entry, "0x" + "0".repeat(63) + "1"))
        assertThrows(IllegalArgumentException::class.java) { ContractService.encode(entry, listOf("0x" + "11".repeat(20))) }
    }

    @Test
    fun `ABI dynamic strings arrays and large quantities survive`() {
        val input = mapOf("name" to "setNames", "inputs" to listOf(mapOf("type" to "string[]")))
        assertTrue(ContractService.encode(input, listOf(listOf("a", "b"))).length > 200)
        val output = mapOf("outputs" to listOf(mapOf("type" to "uint256")))
        assertEquals(BigInteger("f".repeat(64), 16).toString(), ContractService.decode(output, "0x" + "f".repeat(64)))
    }

    @Test
    fun `invalid private key and amount are rejected before external submission`() {
        assertThrows(RequestValidationFailure::class.java) { mapOf("privateKey" to "x").validated("privateKey") }
        assertThrows(RequestValidationFailure::class.java) { mapOf("amount" to "-1").validated("amount") }
        assertThrows(RequestValidationFailure::class.java) { mapOf("to" to "0x123").validated("to") }
    }
}
