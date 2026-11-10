package io.github.gihoon1123.scan

import org.springframework.stereotype.Service

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
        val candidates = abi.filter { it.text("type") == "function" && (it.text("name") == name || signature(it) == name) }
        if (candidates.size > 1) badRequest("Ambiguous method: $name. Use the full signature.")
        val entry = candidates.singleOrNull() ?: badRequest("Method \"$name\" not found in contract ABI")
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
        fun signature(entry: Json): String = AbiCodec.signature(entry)

        fun encode(
            entry: Json,
            params: List<*>,
        ): String = AbiCodec.encode(entry, params)

        fun decode(
            entry: Json,
            value: String,
        ): Any? = AbiCodec.decode(entry, value)
    }
}
