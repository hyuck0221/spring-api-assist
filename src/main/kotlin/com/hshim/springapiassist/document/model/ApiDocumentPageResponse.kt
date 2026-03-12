package com.hshim.springapiassist.document.model

data class ApiDocumentPageResponse(
    val content: List<APIInfoResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)