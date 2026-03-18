package com.hshim.springapiassist.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "api-assist")
data class ApiAssistProperties(
    val apiKey: String = "",
)
