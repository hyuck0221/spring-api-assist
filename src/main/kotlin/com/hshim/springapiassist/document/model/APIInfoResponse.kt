package com.hshim.springapiassist.document.model

data class APIInfoResponse (
    val url: String,
    val method: String,
    val category: String = "",
    val title: String,
    val description: String,
    val requestSchema: Map<String, Any>,
    val responseSchema: Any,
    val requestInfos: List<FieldDetail>,
    val responseInfos: List<FieldDetail>
)