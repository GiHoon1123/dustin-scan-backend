package io.github.gihoon1123.scan

import java.math.BigInteger
import java.time.Instant
import java.time.format.DateTimeFormatterBuilder

// Wire quantities remain strings, avoiding JavaScript's integer precision boundary.
typealias Json = Map<String, Any?>

fun Json.text(
    key: String,
    fallback: String = "",
): String = this[key]?.toString() ?: fallback

@Suppress("UNCHECKED_CAST")
fun Any?.obj(): Json = this as? Map<String, Any?> ?: emptyMap()

fun Any?.objects(): List<Json> = (this as? List<*>)?.map { it.obj() } ?: emptyList()

fun quantity(value: Any?): BigInteger {
    val text = value?.toString().orEmpty()
    return when {
        text.isEmpty() || text == "0x" -> BigInteger.ZERO
        text.startsWith("0x") -> text.drop(2).toBigInteger(16)
        else -> text.toBigInteger()
    }
}

fun display(
    amount: Any?,
    decimals: Int = 18,
    precision: Int = 4,
): String {
    val parts = quantity(amount).divideAndRemainder(BigInteger.TEN.pow(decimals))
    val fraction =
        parts[1]
            .abs()
            .toString()
            .padStart(decimals, '0')
            .take(precision)
            .trimEnd('0')
    return "${parts[0]}.${fraction.ifEmpty { "0" }}"
}

fun toWei(value: String): BigInteger {
    val parts = value.split('.', limit = 2)
    return parts[0].ifEmpty { "0" }.toBigInteger() * BigInteger.TEN.pow(18) +
        parts
            .getOrElse(1) { "" }
            .padEnd(18, '0')
            .take(18)
            .toBigInteger()
}

fun iso(instant: Instant = Instant.now()): String = DateTimeFormatterBuilder().appendInstant(3).toFormatter().format(instant)

fun success(
    data: Any?,
    message: String = "조회 성공",
): Json = linkedMapOf("success" to true, "message" to message, "data" to data, "timestamp" to iso())

fun paginated(
    items: List<Json>,
    page: Int,
    limit: Int,
    count: Long,
    message: String,
): Json {
    val pages = (count + limit - 1) / limit
    return success(
        mapOf(
            "items" to items,
            "pagination" to
                mapOf(
                    "currentPage" to page,
                    "pageSize" to limit,
                    "totalCount" to count,
                    "totalPages" to pages,
                    "hasNext" to (page < pages),
                    "hasPrevious" to (page > 1),
                ),
        ),
        message,
    )
}
