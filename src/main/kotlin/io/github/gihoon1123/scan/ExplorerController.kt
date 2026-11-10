package io.github.gihoon1123.scan

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@ConditionalOnProperty(name = ["scan.role"], havingValue = "api", matchIfMissing = true)
class ExplorerController(
    private val service: ExplorerService,
    private val contracts: ContractService,
    private val chain: ChainClient,
) {
    @GetMapping("/blocks")
    fun blocks(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") limit: Int,
    ): Json = service.blocks(page.coerceAtLeast(1), limit.coerceIn(1, 100))

    @GetMapping("/blocks/number/{number}")
    fun blockNumber(
        @PathVariable number: Long,
    ): Json = success(service.block("number", number), "블록 #$number 조회 성공")

    @GetMapping("/blocks/hash/{hash}")
    fun blockHash(
        @PathVariable hash: String,
    ): Json = success(service.block("hash", hash), "블록 조회 성공")

    @GetMapping("/transactions")
    fun transactions(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") limit: Int,
    ): Json = service.transactions(page.coerceAtLeast(1), limit.coerceIn(1, 100))

    @GetMapping("/transactions/address/{address}")
    fun addressTransactions(
        @PathVariable address: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") limit: Int,
    ): Json = service.transactions(page.coerceAtLeast(1), limit.coerceIn(1, 100), address)

    @GetMapping("/transactions/{hash}")
    fun transaction(
        @PathVariable hash: String,
    ): Json = success(service.transaction(hash), "트랜잭션 조회 성공")

    @GetMapping("/accounts/{address}")
    fun account(
        @PathVariable address: String,
    ): Json = success(service.account(address), "계정 조회 성공")

    @GetMapping("/accounts/{address}/tokens")
    fun tokens(
        @PathVariable address: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") limit: Int,
    ): Json = success(service.tokenBalances(address, page.coerceAtLeast(1), limit.coerceIn(1, 100)), "토큰 자산 목록 조회 성공")

    @GetMapping("/accounts/{address}/token-transfers")
    fun transfers(
        @PathVariable address: String,
        @RequestParam(required = false) token: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") limit: Int,
    ): Json = success(service.transfers(address, token, page.coerceAtLeast(1), limit.coerceIn(1, 100)), "토큰 전송 내역 조회 성공")

    @PostMapping("/accounts/create-wallet")
    @ResponseStatus(HttpStatus.CREATED)
    fun wallet(): Json = success(service.wallet(), "지갑 생성 성공")

    @PostMapping("/accounts/transfer-native")
    @ResponseStatus(HttpStatus.CREATED)
    fun transfer(
        @RequestBody body: Json,
    ): Json {
        body.validated("privateKey", "to", "amount")
        val value =
            chain
                .request(
                    "/transaction/send-native",
                    mapOf(
                        "privateKey" to body.required("privateKey"),
                        "to" to body.required("to"),
                        "amount" to "0x${toWei(body.required("amount")).toString(16)}",
                    ),
                ).obj()
        return success(chain.waitForTransaction(value.text("hash")), "네이티브 토큰 전송 성공")
    }

    @GetMapping("/contracts")
    fun contracts(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") limit: Int,
    ): Json = service.contracts(page.coerceAtLeast(1), limit.coerceIn(1, 100))

    @GetMapping("/contracts/deployer/{address}")
    fun byDeployer(
        @PathVariable address: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") limit: Int,
    ): Json = service.contracts(page.coerceAtLeast(1), limit.coerceIn(1, 100), address)

    @GetMapping("/contracts/{address}")
    fun contract(
        @PathVariable address: String,
    ): Json = success(service.contract(address), "컨트랙트 정보 조회 성공")

    @PutMapping("/contracts/{address}/abi")
    fun abi(
        @PathVariable address: String,
        @RequestBody body: Json,
    ): Json = success(service.updateContract(address, body), "컨트랙트 ABI 업데이트 성공")

    @PostMapping("/contracts/deploy")
    @ResponseStatus(HttpStatus.CREATED)
    fun deploy(
        @RequestBody body: Json,
    ): Json {
        body.validated("bytecode")
        val result = chain.request("/contract/deploy", mapOf("bytecode" to body.required("bytecode"))).obj()
        return success(
            mapOf("transactionHash" to result["hash"], "status" to result["status"], "contractAddress" to null),
            "컨트랙트 배포 트랜잭션 제출 성공",
        )
    }

    @PostMapping("/contracts/{address}/execute")
    @ResponseStatus(HttpStatus.CREATED)
    fun execute(
        @PathVariable address: String,
        @RequestBody body: Json,
    ): Json = success(contracts.execute(address, body, false), "컨트랙트 메서드 실행 성공")

    @PostMapping("/contracts/{address}/call")
    @ResponseStatus(HttpStatus.CREATED)
    fun call(
        @PathVariable address: String,
        @RequestBody body: Json,
    ): Json = success(contracts.execute(address, body, true), "컨트랙트 메서드 호출 성공")
}

fun Json.required(key: String): String = (this[key] as? String)?.takeIf { it.isNotEmpty() } ?: badRequest("$key should not be empty")
