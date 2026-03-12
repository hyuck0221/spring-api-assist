package com.hshim.springapiassist.configuration.properties

import com.hshim.springapiassist.log.enums.LogFileFormat
import org.springframework.boot.context.properties.ConfigurationProperties


@ConfigurationProperties(prefix = "api-assist.log")
data class ApiLogProperties(
    val enabled: Boolean = true,
    val appName: String = "",
    val includePaths: List<String> = emptyList(),
    val excludePaths: List<String> = emptyList(),
    val maskHeaders: List<String> = listOf("Authorization", "Cookie", "Set-Cookie"),
    val maskRequestBody: Boolean = false,
    val maskResponseBody: Boolean = false,
    val maxBodySize: Int = 10000,
    val storage: StorageProperties = StorageProperties(),
    val view: ViewProperties = ViewProperties(),
) {
    data class StorageProperties(
        val db: DBStorageProperties = DBStorageProperties(),
        val localFile: LocalFileStorageProperties = LocalFileStorageProperties(),
        val supabaseDb: SupabaseDBStorageProperties = SupabaseDBStorageProperties(),
        val supabaseS3: SupabaseS3StorageProperties = SupabaseS3StorageProperties(),
    )

    data class DBStorageProperties(
        val enabled: Boolean = false,
        val tableName: String = "api_logs",
        val autoCreateTable: Boolean = true,
    )

    data class LocalFileStorageProperties(
        val enabled: Boolean = false,
        val path: String = "./logs/api",
        val logsPerFile: Int = 1000,
        val flushIntervalSeconds: Long = 0,
        val format: LogFileFormat = LogFileFormat.JSON,
    )

    data class SupabaseDBStorageProperties(
        val enabled: Boolean = false,
        val jdbcUrl: String = "",
        val username: String = "postgres",
        val password: String = "",
        val tableName: String = "api_logs",
        val autoCreateTable: Boolean = true,
    )

    data class SupabaseS3StorageProperties(
        val enabled: Boolean = false,
        val endpointUrl: String = "",
        val accessKeyId: String = "",
        val secretAccessKey: String = "",
        val region: String = "ap-northeast-1",
        val bucket: String = "api-logs",
        val keyPrefix: String = "logs/",
        val logsPerFile: Int = 1000,
        val format: LogFileFormat = LogFileFormat.JSON,
    )

    data class ViewProperties(
        val enabled: Boolean = false,
        val basePath: String = "/api/logs",
        val apiKey: String = "",
    )
}