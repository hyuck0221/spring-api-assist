package com.hshim.springapiassist.log.view.dto

data class ApiLogQuery(
    val appName: String? = null,
    val method: String? = null,
    val url: String? = null,
    val statusCode: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val minProcessingTimeMs: Long? = null,
    val remoteAddr: String? = null,
    val serverName: String? = null,
    val page: Int = 0,
    val size: Int = 20,
    val sortBy: String = "request_time",
    val sortDir: String = "DESC",
)
