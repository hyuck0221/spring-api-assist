package com.hshim.springapiassist.document.model

import com.hshim.springapiassist.document.enums.ParameterType

data class FieldDetail(
    val path: String,
    val type: String,
    val description: String,
    val nullable: Boolean,
    val parameterType: ParameterType? = null
)

