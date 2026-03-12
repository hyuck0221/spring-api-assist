package com.hshim.springapiassist.log.model

import util.CommonUtil.ulid
import java.time.LocalDateTime

data class ApiLogEntry(
    val id: String = ulid(),
    val appName: String? = null,
    val url: String,
    val method: String,
    val queryParams: Map<String, List<String>> = emptyMap(),
    val requestHeaders: Map<String, String> = emptyMap(),
    val requestBody: String? = null,
    val responseStatus: Int,
    val responseContentType: String? = null,
    val responseBody: String? = null,
    val requestTime: LocalDateTime,
    val responseTime: LocalDateTime,
    val processingTimeMs: Long,
    val serverName: String? = null,
    val serverPort: Int? = null,
    val remoteAddr: String? = null,
)