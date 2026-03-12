package com.hshim.springapiassist.log.view.dto

import java.io.File
import java.time.Instant

data class ApiLogFileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Instant,
) {
    constructor(file: File) : this(
        name = file.name,
        path = file.path,
        size = file.length(),
        lastModified = Instant.ofEpochMilli(file.lastModified()),
    )
}