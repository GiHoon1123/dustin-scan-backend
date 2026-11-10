package io.github.gihoon1123.scan

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp

@Repository
class ExplorerStore(
    val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
) {
    private val tables = setOf("blocks", "transactions", "transaction_receipts", "accounts", "contracts", "tokens", "token_transfers")
    private val stringNumbers = setOf("number", "blockNumber", "timestamp", "value", "balance", "gasUsed", "cumulativeGasUsed")

    fun rows(
        sql: String,
        vararg args: Any?,
    ): List<Json> = jdbc.query(sql, { rs, _ -> row(rs) }, *args)

    fun one(
        sql: String,
        vararg args: Any?,
    ): Json? = rows(sql, *args).firstOrNull()

    fun count(
        table: String,
        clause: String = "",
        vararg args: Any?,
    ): Long {
        require(table in tables)
        return jdbc.queryForObject("SELECT COUNT(*) FROM \"$table\" $clause", Long::class.java, *args) ?: 0
    }

    fun insert(
        table: String,
        values: Json,
        conflict: String? = null,
    ) {
        require(table in tables)
        require(values.keys.all { it.matches(Regex("[A-Za-z]+")) })
        val jsonFields = setOf("raw", "abi", "logs")
        val columns = values.keys.joinToString(",") { "\"$it\"" }
        val slots = values.keys.joinToString(",") { if (it in jsonFields) "?::jsonb" else "?" }
        val args =
            values
                .map { (key, value) ->
                    if (key in jsonFields && value != null) mapper.writeValueAsString(value) else value
                }.toTypedArray()
        val suffix = if (conflict == null) "" else " ON CONFLICT (\"$conflict\") DO NOTHING"
        jdbc.update("INSERT INTO \"$table\" ($columns) VALUES ($slots)$suffix", *args)
    }

    fun updateContract(
        address: String,
        values: Json,
    ) {
        val allowed = setOf("abi", "bytecode", "name", "sourceCode", "compilerVersion", "optimization")
        val updates = values.filter { it.key in allowed && it.value != null }
        if (updates.isEmpty()) return
        val assignments = updates.keys.joinToString(",") { "\"$it\" = ?" + if (it == "abi") "::jsonb" else "" }
        val args = updates.map { (key, value) -> if (key == "abi") mapper.writeValueAsString(value) else value } + address
        jdbc.update("UPDATE contracts SET $assignments WHERE address = ?", *args.toTypedArray())
    }

    private fun row(rs: ResultSet): Json =
        buildMap {
            for (i in 1..rs.metaData.columnCount) {
                val key = rs.metaData.getColumnLabel(i)
                val value = rs.getObject(i)
                put(
                    key,
                    when {
                        value == null -> null
                        key in setOf("raw", "abi", "logs") -> mapper.readValue(value.toString(), object : TypeReference<Any?>() {})
                        value is Timestamp -> iso(value.toInstant())
                        key in stringNumbers -> if (value is BigDecimal) value.toPlainString() else value.toString()
                        else -> value
                    },
                )
            }
        }
}
