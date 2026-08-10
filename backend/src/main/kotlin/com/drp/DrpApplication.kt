package com.drp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DrpApplication

fun main(args: Array<String>) {
    runApplication<DrpApplication>(*args)
}
