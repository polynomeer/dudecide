package com.dudecide.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class DudecideApplication

fun main(args: Array<String>) {
    runApplication<DudecideApplication>(*args)
}
