package io.github.gihoon1123.scan

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@ConditionalOnProperty(name = ["scan.role"], havingValue = "api", matchIfMissing = true)
class ProxyController(
    private val chain: ChainClient,
) {
    @PostMapping("/staking/{operation:deposit|set-withdrawal-address|request-withdrawal}")
    @ResponseStatus(HttpStatus.CREATED)
    fun stake(
        @PathVariable operation: String,
        @RequestBody body: Json,
    ): Json {
        body.validated("privateKey")
        if (operation == "deposit") body.validated("amount")
        if (operation == "set-withdrawal-address") body.validated("withdrawalAddress")
        val payload = mutableMapOf<String, Any?>("privateKey" to body.required("privateKey"))
        when (operation) {
            "deposit" -> payload["amount"] = "0x${toWei(body.required("amount")).toString(16)}"
            "set-withdrawal-address" -> payload["withdrawalAddress"] = body.required("withdrawalAddress")
        }
        val result = chain.request("/staking/$operation", payload).obj()
        val message =
            when (operation) {
                "deposit" -> "스테이킹 예치 성공"
                "set-withdrawal-address" -> "출금 주소 설정 성공"
                else -> "출금 요청 성공"
            }
        return success(chain.waitForTransaction(result.text("hash")), message)
    }

    @GetMapping("/staking/validator/{address}")
    fun validator(
        @PathVariable address: String,
    ): Json = success(validatorDto(chain.request("/staking/validator/$address").obj()), "Validator 정보 조회 성공")

    @GetMapping("/staking/validators")
    fun validators(): Json {
        val values =
            chain
                .request("/staking/validators")
                .obj()["validators"]
                .objects()
                .map(::validatorDto)
        return success(mapOf("validators" to values, "total" to values.size), "Validator 목록 조회 성공")
    }

    @GetMapping("/staking/stats")
    fun stats(): Json {
        val value = chain.request("/staking/stats").obj()
        val result = value.filterKeys { it in setOf("totalValidators", "activeValidators", "maxValidators") }.toMutableMap()
        for (key in listOf("totalStaked", "totalRewards", "totalSlashed", "minStake")) {
            val wei = quantity(value[key]).toString()
            result[key] = display(wei)
            result["${key}Wei"] = wei
        }
        result["withdrawalDelay"] = quantity(value["withdrawalDelay"]).toString()
        return success(result, "스테이킹 통계 조회 성공")
    }

    @PostMapping("/stablecoin/{operation:deposit|mint|redeem|withdraw|liquidate|transfer-stablecoin}")
    @ResponseStatus(HttpStatus.CREATED)
    fun stablecoin(
        @PathVariable operation: String,
        @RequestBody body: Json,
    ): Json {
        body.validated("privateKey")
        when (operation) {
            "liquidate" -> body.validated("userAddress")
            "mint", "redeem" -> body.validated("stablecoinAmount")
            "transfer-stablecoin" -> body.validated("to", "amount")
            else -> body.validated("amount")
        }
        val payload = mutableMapOf<String, Any?>("privateKey" to body.required("privateKey"))
        if (operation == "liquidate") {
            payload["userAddress"] = body.required("userAddress")
        } else {
            val field = if (operation in setOf("mint", "redeem")) "stablecoinAmount" else "amount"
            payload[field] = "0x${toWei(body.required(field)).toString(16)}"
            if (operation == "transfer-stablecoin") payload["to"] = body.required("to")
        }
        val path = if (operation == "transfer-stablecoin") "transfer" else operation
        val result = chain.request("/stablecoin/$path", payload).obj()
        return if (operation == "transfer-stablecoin") success(chain.waitForTransaction(result.text("hash")), "스테이블코인 전송 성공") else result
    }

    @GetMapping("/stablecoin/position/{address}")
    fun position(
        @PathVariable address: String,
    ): Json {
        val value = chain.request("/stablecoin/position/$address").obj()
        val collateral = quantity(value["collateralAmount"]).toString()
        val debt = quantity(value["debtAmount"]).toString()
        return mapOf(
            "collateralAmount" to display(collateral),
            "collateralAmountWei" to collateral,
            "debtAmount" to display(debt),
            "debtAmountWei" to debt,
            "collateralRatio" to quantity(value["collateralRatio"]).toString(),
        )
    }

    @GetMapping("/stablecoin/health/{address}")
    fun health(
        @PathVariable address: String,
    ): Json =
        success(
            mapOf(
                "isHealthy" to (quantity(chain.request("/stablecoin/health/$address").obj()["result"]) != java.math.BigInteger.ZERO),
            ),
            "헬스체크 확인 성공",
        )

    @GetMapping("/stablecoin/balance/{address}")
    fun balance(
        @PathVariable address: String,
    ): Json {
        val wei = quantity(chain.request("/stablecoin/balance/$address").obj()["balance"]).toString()
        return success(mapOf("balance" to display(wei), "balanceWei" to wei), "스테이블코인 잔액 조회 성공")
    }

    private fun validatorDto(value: Json): Json {
        val result = value.filterKeys { it in setOf("validatorAddress", "status", "withdrawalAddress") }.toMutableMap()
        for (key in listOf("stakedAmount", "totalRewards", "slashedAmount")) {
            val wei = quantity(value[key]).toString()
            result[key] = display(wei)
            result["${key}Wei"] = wei
        }
        for (key in listOf("activatedAt", "exitRequestedAt")) result[key] = quantity(value[key]).toString()
        return result
    }
}
