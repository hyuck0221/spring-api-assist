package com.hshim.springapiassist

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.runApplication

@SpringBootApplication(exclude = [DataSourceAutoConfiguration::class])
class SpringApiAssistApplication

fun main(args: Array<String>) {
    runApplication<SpringApiAssistApplication>(*args)
}
