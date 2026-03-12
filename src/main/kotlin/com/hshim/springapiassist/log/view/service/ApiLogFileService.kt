package com.hshim.springapiassist.log.view.service

import com.hshim.springapiassist.log.view.dto.ApiLogFileInfo
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException

class ApiLogFileService {

    private val log = LoggerFactory.getLogger(ApiLogFileService::class.java)

    fun listFiles(
        directory: String,
        maxFiles: Int,
        format: String,
    ): List<ApiLogFileInfo> {
        val safeMax = maxFiles.coerceIn(1, 100)
        val dir = File(directory).canonicalFile

        if (!dir.exists() || !dir.isDirectory) {
            log.debug("ApiLog file listing: directory not found or not a directory — {}", dir.absolutePath)
            return emptyList()
        }

        val extension = when (format.lowercase()) {
            "csv" -> ".csv"
            else -> ".jsonl"
        }

        return dir.listFiles { it.isFile && it.name.endsWith(extension) }
            ?.sortedByDescending { it.lastModified() }
            ?.take(safeMax)
            ?.map { ApiLogFileInfo(it) }
            ?: emptyList()
    }

    fun readFileContent(path: String, directory: String): String {
        require(path.isNotBlank()) { "path must not be blank" }

        val dir = File(directory).canonicalFile
        val target = File(path).canonicalFile

        if (!target.absolutePath.startsWith(dir.absolutePath + File.separator) &&
            target.absolutePath != dir.absolutePath
        ) throw SecurityException("Access denied: '$path' is outside the allowed directory '${dir.absolutePath}'")

        if (!target.exists()) throw FileNotFoundException("File not found: ${target.absolutePath}")
        if (!target.isFile) throw IllegalArgumentException("Not a file: ${target.absolutePath}")

        return target.readText(Charsets.UTF_8)
    }
}
