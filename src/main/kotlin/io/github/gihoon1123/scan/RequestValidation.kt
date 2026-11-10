package io.github.gihoon1123.scan

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

class RequestValidationFailure(
    val errors: List<String>,
) : RuntimeException("Validation failed")

fun Json.validated(vararg fields: String): Json {
    val errors = mutableListOf<String>()
    for (field in fields) {
        val value = this[field]
        if (value !is String || value.isEmpty()) {
            errors += "$field should not be empty"
            continue
        }
        val regex =
            when (field) {
                "privateKey" -> Regex("0x[a-fA-F0-9]{64}")
                "to", "userAddress", "withdrawalAddress" -> Regex("0x[a-fA-F0-9]{40}")
                "amount", "stablecoinAmount" -> Regex("\\d+(\\.\\d+)?")
                "bytecode" -> Regex("0x[a-fA-F0-9]*")
                else -> null
            }
        if (regex != null && !value.matches(regex)) {
            errors +=
                when (field) {
                    "privateKey" -> "privateKey must be a valid private key"
                    "bytecode" -> "bytecode must be a valid hex string starting with 0x"
                    "amount", "stablecoinAmount" -> "$field must be a decimal DSTN string (e.g., \"10\" or \"0.5\")"
                    else -> "$field must be a valid Ethereum address"
                }
        }
    }
    if (errors.isNotEmpty()) throw RequestValidationFailure(errors)
    return this
}

@RestControllerAdvice
class ValidationErrors {
    @ExceptionHandler(RequestValidationFailure::class)
    fun invalid(error: RequestValidationFailure): ResponseEntity<Json> =
        ResponseEntity.badRequest().body(
            mapOf("message" to error.errors, "error" to "Bad Request", "statusCode" to 400),
        )
}
