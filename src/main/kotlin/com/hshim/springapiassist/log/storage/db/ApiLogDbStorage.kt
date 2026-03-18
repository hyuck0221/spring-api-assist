package com.hshim.springapiassist.log.storage.db

import com.hshim.springapiassist.log.storage.ApiLogStorage
import com.hshim.springapiassist.configuration.properties.ApiLogProperties
import com.hshim.springapiassist.log.model.ApiLogEntry
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import util.ClassUtil.classToJson
import java.sql.Timestamp

class ApiLogDbStorage(
    private val jdbcTemplate: JdbcTemplate,
    private val properties: ApiLogProperties,
) : ApiLogStorage {

    private val log = LoggerFactory.getLogger(ApiLogDbStorage::class.java)
    private val dbProps = properties.storage.db

    @PostConstruct
    fun init() {
        if (dbProps.autoCreateTable) {
            createTableIfNotExists()
        }
    }

    private fun createTableIfNotExists() {
        val t = dbProps.tableName
        try {
            jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS $t (
                    id                    VARCHAR(36)  PRIMARY KEY,
                    app_name              VARCHAR(255),
                    url                   TEXT         NOT NULL,
                    method                VARCHAR(10)  NOT NULL,
                    query_params          TEXT,
                    request_headers       TEXT,
                    request_body          TEXT,
                    response_status       INT,
                    response_content_type VARCHAR(255),
                    response_body         TEXT,
                    request_time          TIMESTAMP    NOT NULL,
                    response_time         TIMESTAMP,
                    processing_time_ms    BIGINT,
                    server_name           VARCHAR(255),
                    server_port           INT,
                    remote_addr           VARCHAR(255),
                    created_at            TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
                )
                """.trimIndent(),
            )
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_${t}_request_time       ON $t (request_time)")
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_${t}_response_status     ON $t (response_status)")
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_${t}_method              ON $t (method)")
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_${t}_app_name            ON $t (app_name)")
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_${t}_processing_time_ms  ON $t (processing_time_ms)")
            log.info("ApiLog DB table '$t' is ready.")
        } catch (e: Exception) {
            log.warn(
                "Could not create table '$t'. " +
                        "It may already exist or you may have insufficient permissions.",
                e,
            )
        }
    }

    override fun save(entry: ApiLogEntry) {
        jdbcTemplate.update(
            """
            INSERT INTO ${dbProps.tableName}
            (id, app_name, url, method, query_params, request_headers, request_body,
             response_status, response_content_type, response_body,
             request_time, response_time, processing_time_ms,
             server_name, server_port, remote_addr)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            entry.id,
            entry.appName,
            entry.url,
            entry.method,
            entry.queryParams.classToJson(),
            entry.requestHeaders.classToJson(),
            entry.requestBody,
            entry.responseStatus,
            entry.responseContentType,
            entry.responseBody,
            Timestamp.valueOf(entry.requestTime),
            entry.responseTime?.let { Timestamp.valueOf(it) },
            entry.processingTimeMs,
            entry.serverName,
            entry.serverPort,
            entry.remoteAddr,
        )
    }
}
