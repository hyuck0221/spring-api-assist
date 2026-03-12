package com.hshim.springapiassist.log.view.dto

data class ApiLogStats(
    val totalCount: Long,
    val countByMethod: Map<String, Long>,
    val countByStatus: Map<Int, Long>,
    val countByAppName: Map<String, Long>,
    val avgProcessingTimeMs: Double,
    val maxProcessingTimeMs: Long,
    val p99ProcessingTimeMs: Long?,
)
