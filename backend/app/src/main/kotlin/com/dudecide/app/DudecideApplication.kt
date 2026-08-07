package com.dudecide.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DudecideApplication

fun main(args: Array<String>) {
    runApplication<DudecideApplication>(*args)
}
