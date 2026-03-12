package com.hshim.springapiassist.log.view.dto

import com.hshim.springapiassist.log.model.ApiLogEntry

data class ApiLogPage(
    val content: List<ApiLogEntry>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
