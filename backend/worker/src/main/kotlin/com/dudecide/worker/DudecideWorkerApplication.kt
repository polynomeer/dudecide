package com.dudecide.worker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DudecideWorkerApplication

fun main(args: Array<String>) {
    runApplication<DudecideWorkerApplication>(*args)
}
