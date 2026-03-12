package com.hshim.springapiassist.log.storage.supabase

import com.hshim.springapiassist.log.storage.ApiLogStorage
import com.hshim.springapiassist.configuration.properties.ApiLogProperties
import com.hshim.springapiassist.log.model.ApiLogEntry
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import util.ClassUtil.classToJson
import java.sql.Timestamp

class ApiLogSupabaseDbStorage(private val properties: ApiLogProperties) : ApiLogStorage {

    private val log = LoggerFactory.getLogger(ApiLogSupabaseDbStorage::class.java)
    private val supabaseDbProps = properties.storage.supabaseDb
    private lateinit var jdbcTemplate: JdbcTemplate
    private lateinit var dataSource: DriverManagerDataSource

    @PostConstruct
    fun init() {
        require(supabaseDbProps.jdbcUrl.isNotBlank()) {
            "apilog.storage.supabase-db.jdbc-url must not be blank"
        }
        require(supabaseDbProps.password.isNotBlank()) {
            "apilog.storage.supabase-db.password must not be blank"
        }

        dataSource = DriverManagerDataSource().apply {
            setDriverClassName("org.postgresql.Driver")
            url = supabaseDbProps.jdbcUrl
            username = supabaseDbProps.username
            password = supabaseDbProps.password
        }
        jdbcTemplate = JdbcTemplate(dataSource)

        if (supabaseDbProps.autoCreateTable) {
            createTableIfNotExists()
        }

        log.info("ApiLog Supabase DB storage initialized. Table: ${supabaseDbProps.tableName}")
    }

    private fun createTableIfNotExists() {
        val t = supabaseDbProps.tableName
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
                    request_time          TIMESTAMPTZ  NOT NULL,
                    response_time         TIMESTAMPTZ  NOT NULL,
                    processing_time_ms    BIGINT       NOT NULL,
                    server_name           VARCHAR(255),
                    server_port           INT,
                    remote_addr           VARCHAR(255),
                    created_at            TIMESTAMPTZ  DEFAULT NOW()
                )
                """.trimIndent(),
            )
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_${t}_request_time       ON $t (request_time)")
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_${t}_response_status     ON $t (response_status)")
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_${t}_method              ON $t (method)")
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_${t}_app_name            ON $t (app_name)")
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_${t}_processing_time_ms  ON $t (processing_time_ms)")
            log.info("Supabase DB table '$t' is ready.")
        } catch (e: Exception) {
            log.warn(
                "Could not create table '$t' in Supabase DB. " +
                        "It may already exist or you may have insufficient permissions.",
                e,
            )
        }
    }

    override fun save(entry: ApiLogEntry) {
        jdbcTemplate.update(
            """
            INSERT INTO ${supabaseDbProps.tableName}
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
            Timestamp.valueOf(entry.responseTime),
            entry.processingTimeMs,
            entry.serverName,
            entry.serverPort,
            entry.remoteAddr,
        )
    }
}
