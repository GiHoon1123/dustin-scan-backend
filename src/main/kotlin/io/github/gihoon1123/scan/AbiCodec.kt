package io.github.gihoon1123.scan

import org.web3j.crypto.Hash
import java.math.BigInteger

// ABI offsets are byte offsets relative to the enclosing tuple or array body.
object AbiCodec {
    private val modulus = BigInteger.ONE.shiftLeft(256)

    private data class Shape(
        val atom: String,
        val fields: List<Shape> = emptyList(),
        val child: Shape? = null,
        val length: Int? = null,
        val name: String = "",
    ) {
        val dynamic: Boolean get() = if (child != null) length == null || child.dynamic else atom in setOf("bytes", "string") || fields.any { it.dynamic }
        val size: Int get() =
            if (dynamic) {
                32
            } else if (child != null) {
                Math.multiplyExact(length!!, child.size)
            } else if (atom == "tuple") {
                fields.sumOf { it.size }
            } else {
                32
            }
        val canonical: String get() =
            if (child != null) {
                child.canonical + "[${length ?: ""}]"
            } else if (atom == "tuple") {
                fields.joinToString(",", "(", ")") { it.canonical }
            } else {
                when (atom) {
                    "uint" -> "uint256"
                    "int" -> "int256"
                    else -> atom
                }
            }
    }

    fun signature(entry: Json): String = entry.text("name") + entry["inputs"].objects().map(::shape).joinToString(",", "(", ")") { it.canonical }

    fun encode(
        entry: Json,
        params: List<*>,
    ): String = Hash.sha3String(signature(entry)).take(10) + hex(sequence(entry["inputs"].objects().map(::shape), params))

    fun decode(
        entry: Json,
        encoded: String,
    ): Any? {
        val data = bytes(encoded)
        val values = readSequence(entry["outputs"].objects().map(::shape), data, 0)
        return if (values.size == 1) values[0] else values
    }

    private fun shape(value: Json): Shape {
        val type = value.text("type")
        val suffix = Regex("^(.*)\\[(\\d*)]$").matchEntire(type)
        if (suffix != null) {
            val child = shape(value + ("type" to suffix.groupValues[1]))
            return Shape("array", child = child, length = suffix.groupValues[2].takeIf { it.isNotEmpty() }?.toInt(), name = value.text("name"))
        }
        return Shape(type, value["components"].objects().map(::shape), name = value.text("name"))
    }

    private fun sequence(
        types: List<Shape>,
        values: List<*>,
    ): ByteArray {
        require(types.size == values.size) { "Argument count mismatch" }
        val head = mutableListOf<ByteArray>()
        val tail = mutableListOf<ByteArray>()
        var offset = types.sumOf { if (it.dynamic) 32 else it.size }
        types.forEachIndexed { index, type ->
            val encoded = value(type, values[index])
            if (type.dynamic) {
                head += word(offset.toBigInteger())
                tail += encoded
                offset = Math.addExact(offset, encoded.size)
            } else {
                head += encoded
            }
        }
        return (head + tail).fold(byteArrayOf()) { total, part -> total + part }
    }

    private fun value(
        type: Shape,
        input: Any?,
    ): ByteArray {
        if (type.child != null) {
            val values = input as? List<*> ?: throw IllegalArgumentException("Array argument required")
            require(type.length == null || values.size == type.length) { "Array length mismatch" }
            return (if (type.length == null) word(values.size.toBigInteger()) else byteArrayOf()) + sequence(List(values.size) { type.child }, values)
        }
        if (type.atom == "tuple") {
            val values =
                when (input) {
                    is List<*> -> input
                    is Map<*, *> -> type.fields.map { input[it.name] }
                    else -> throw IllegalArgumentException("Tuple argument required")
                }
            return sequence(type.fields, values)
        }
        return when {
            type.atom.startsWith("uint") || type.atom.startsWith("int") -> {
                val signed = type.atom.startsWith("int")
                val bits =
                    type.atom
                        .removePrefix(if (signed) "int" else "uint")
                        .ifEmpty { "256" }
                        .toInt()
                require(bits in 8..256 && bits % 8 == 0)
                val n = quantity(input)
                val limit = BigInteger.ONE.shiftLeft(if (signed) bits - 1 else bits)
                require(n >= if (signed) limit.negate() else BigInteger.ZERO) { "Integer below range" }
                require(n < limit) { "Integer above range" }
                word(n)
            }
            type.atom == "address" -> {
                val address = input.toString().removePrefix("0x")
                require(address.matches(Regex("[0-9a-fA-F]{40}"))) { "Invalid address" }
                ByteArray(12) + bytes(address)
            }
            type.atom == "bool" -> {
                require(input is Boolean) { "Boolean required" }
                word(if (input) BigInteger.ONE else BigInteger.ZERO)
            }
            type.atom == "string" -> dynamicBytes((input as? String ?: throw IllegalArgumentException("String required")).toByteArray(Charsets.UTF_8))
            type.atom == "bytes" -> dynamicBytes(bytes(input.toString()))
            type.atom.startsWith("bytes") || type.atom == "function" -> {
                val size = if (type.atom == "function") 24 else type.atom.drop(5).toInt()
                val data = bytes(input.toString())
                require(size in 1..32 && data.size == size) { "Invalid fixed bytes length" }
                data + ByteArray(32 - size)
            }
            else -> throw IllegalArgumentException("Unsupported ABI type ${type.atom}")
        }
    }

    private fun readSequence(
        types: List<Shape>,
        data: ByteArray,
        base: Int,
    ): List<Any?> {
        var cursor = base
        return types.map { type ->
            val at = if (type.dynamic) Math.addExact(base, number(data, cursor).intValueExact()) else cursor
            val decoded = readValue(type, data, at)
            cursor = Math.addExact(cursor, if (type.dynamic) 32 else type.size)
            decoded
        }
    }

    private fun readValue(
        type: Shape,
        data: ByteArray,
        at: Int,
    ): Any? {
        if (type.child != null) {
            val length = type.length ?: number(data, at).intValueExact()
            require(length >= 0 && length <= data.size / 32) { "Invalid ABI array size" }
            return readSequence(List(length) { type.child }, data, if (type.length == null) Math.addExact(at, 32) else at)
        }
        if (type.atom == "tuple") return readSequence(type.fields, data, at)
        return when {
            type.atom.startsWith("uint") -> number(data, at).toString()
            type.atom.startsWith("int") -> {
                val n = number(data, at)
                (if (n.testBit(255)) n - modulus else n).toString()
            }
            type.atom == "address" -> "0x" + hex(slice(data, at + 12, 20))
            type.atom == "bool" -> {
                val n = number(data, at)
                require(n == BigInteger.ZERO || n == BigInteger.ONE)
                n == BigInteger.ONE
            }
            type.atom == "string" || type.atom == "bytes" -> {
                val length = number(data, at).intValueExact()
                val result = slice(data, Math.addExact(at, 32), length)
                if (type.atom == "string") result.toString(Charsets.UTF_8) else "0x" + hex(result)
            }
            type.atom.startsWith("bytes") || type.atom == "function" -> "0x" + hex(slice(data, at, if (type.atom == "function") 24 else type.atom.drop(5).toInt()))
            else -> throw IllegalArgumentException("Unsupported ABI type ${type.atom}")
        }
    }

    private fun slice(
        data: ByteArray,
        at: Int,
        size: Int,
    ): ByteArray {
        require(at >= 0 && size >= 0 && at <= data.size - size) { "ABI data out of bounds" }
        return data.copyOfRange(at, at + size)
    }

    private fun number(
        data: ByteArray,
        at: Int,
    ): BigInteger = BigInteger(1, slice(data, at, 32))

    private fun word(value: BigInteger): ByteArray = bytes(value.mod(modulus).toString(16).padStart(64, '0'))

    private fun dynamicBytes(value: ByteArray): ByteArray = word(value.size.toBigInteger()) + value + ByteArray((32 - value.size % 32) % 32)

    private fun bytes(input: String): ByteArray {
        val value = input.removePrefix("0x")
        require(value.length % 2 == 0 && value.matches(Regex("[0-9a-fA-F]*"))) { "Invalid hex bytes" }
        return value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun hex(value: ByteArray): String = value.joinToString("") { "%02x".format(it) }
}
