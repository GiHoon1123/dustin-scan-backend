package io.github.gihoon1123.scan

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class ScanApplication

fun main(args: Array<String>) {
    runApplication<ScanApplication>(*args)
}
