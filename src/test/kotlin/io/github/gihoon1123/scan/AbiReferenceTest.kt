package io.github.gihoon1123.scan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class AbiReferenceTest {
    @Test
    fun `ethers reference vectors match tuple array integer and bytes encoding`() {
        val input = javaClass.getResourceAsStream("/abi-reference-vectors.json")!!
        val vectors =
            JsonMapper
                .builder()
                .build()
                .readValue(input, List::class.java)
                .map { it.obj() }
        for (vector in vectors) {
            val entry = vector["entry"].obj()
            assertEquals(vector["encoded"], AbiCodec.encode(entry, vector["params"] as List<*>), entry.text("name"))
            val expected = vector["result"] as List<*>
            assertEquals(if (expected.size == 1) expected[0] else expected, AbiCodec.decode(entry, vector.text("encodedResult")), entry.text("name"))
        }
    }
}
