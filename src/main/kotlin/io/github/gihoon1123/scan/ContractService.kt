package io.github.gihoon1123.scan

import org.springframework.stereotype.Service
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import java.math.BigInteger

@Service
class ContractService(
    private val explorer: ExplorerService,
    private val chain: ChainClient,
) {
    fun execute(
        address: String,
        body: Json,
        readOnly: Boolean,
    ): Json {
        val abi = explorer.contractEntity(address)["abi"].objects()
        if (abi.isEmpty()) badRequest("Contract ABI not found. Please upload ABI first using PUT /contracts/$address/abi")
        val name = body.text("methodName")
        val params = body["params"] as? List<*> ?: badRequest("params must be an array")
        val entry =
            abi.firstOrNull { it.text("type") == "function" && (it.text("name") == name || signature(it) == name) }
                ?: badRequest("Method \"$name\" not found in contract ABI")
        return try {
            val data = encode(entry, params)
            val response =
                chain
                    .request(
                        if (readOnly) "/contract/call" else "/contract/execute",
                        mapOf("to" to address, "data" to data),
                    ).obj()
            if (readOnly) {
                val decoded = runCatching { decode(entry, response.text("result")) }.getOrNull()
                mapOf("result" to response["result"], "gasUsed" to response["gasUsed"], "decodedResult" to decoded)
            } else {
                mapOf("transactionHash" to response["hash"], "status" to response["status"])
            }
        } catch (error: ReflectiveOperationException) {
            badRequest("Failed to ${if (readOnly) "call" else "execute"} contract method: ${error.message}")
        } catch (error: IllegalArgumentException) {
            badRequest("Failed to ${if (readOnly) "call" else "execute"} contract method: ${error.message}")
        }
    }

    companion object {
        fun signature(entry: Json): String = entry.text("name") + "(" + entry["inputs"].objects().joinToString(",") { it.text("type") } + ")"

        fun encode(
            entry: Json,
            params: List<*>,
        ): String {
            val inputs = entry["inputs"].objects()
            require(inputs.size == params.size) { "Argument count mismatch" }
            val values = inputs.mapIndexed { index, input -> TypeDecoder.instantiateType(input.text("type"), params[index]) }
            return FunctionEncoder.encode(Function(entry.text("name"), values, emptyList()))
        }

        @Suppress("UNCHECKED_CAST")
        fun decode(
            entry: Json,
            value: String,
        ): Any? {
            val refs = entry["outputs"].objects().map { TypeReference.makeTypeReference(it.text("type")) as TypeReference<Type<*>> }
            val values = FunctionReturnDecoder.decode(value, refs).map(::wireValue)
            return if (values.size == 1) values[0] else values
        }

        private fun wireValue(type: Type<*>): Any? =
            when (val value = type.value) {
                is BigInteger -> value.toString()
                is ByteArray -> "0x" + value.joinToString("") { "%02x".format(it) }
                is List<*> -> value.map { if (it is Type<*>) wireValue(it) else it }
                else -> value
            }
    }
}
