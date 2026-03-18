package com.hshim.springapiassist.log.view.service

import com.hshim.springapiassist.configuration.properties.ApiLogProperties
import com.hshim.springapiassist.log.model.ApiLogEntry
import com.hshim.springapiassist.log.view.dto.ApiLogPage
import com.hshim.springapiassist.log.view.dto.ApiLogQuery
import com.hshim.springapiassist.log.view.dto.ApiLogStats
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import util.ClassUtil.jsonToClass
import java.sql.ResultSet
import java.time.LocalDateTime

class ApiLogViewService(
    private val jdbcTemplate: JdbcTemplate,
    private val properties: ApiLogProperties,
) {

    private val table get() = properties.storage.db.tableName

    // Allowed sort columns — prevent SQL injection via user-supplied sortBy
    private val allowedSortColumns = setOf(
        "request_time", "processing_time_ms", "response_status", "url", "method", "app_name",
    )

    fun queryLogs(q: ApiLogQuery): ApiLogPage {
        val safePage = maxOf(0, q.page)
        val safeSize = q.size.coerceIn(1, 200)
        val safeSort = if (q.sortBy in allowedSortColumns) q.sortBy else "request_time"
        val safeDir = if (q.sortDir.uppercase() == "ASC") "ASC" else "DESC"

        val (whereClause, params) = buildWhere(q)

        val countSql = "SELECT COUNT(*) FROM $table $whereClause"
        val total = jdbcTemplate.queryForObject(countSql, Long::class.java, *params.toTypedArray()) ?: 0L

        val dataSql = """
            SELECT * FROM $table
            $whereClause
            ORDER BY $safeSort $safeDir
            LIMIT $safeSize OFFSET ${safePage * safeSize}
        """.trimIndent()

        val rows = jdbcTemplate.query(dataSql, rowMapper(), *params.toTypedArray())

        val totalPages = if (total == 0L) 0 else ((total - 1) / safeSize + 1).toInt()
        return ApiLogPage(
            content = rows,
            page = safePage,
            size = safeSize,
            totalElements = total,
            totalPages = totalPages,
        )
    }

    fun findById(id: String): ApiLogEntry? {
        val sql = "SELECT * FROM $table WHERE id = ?"
        return jdbcTemplate.query(sql, rowMapper(), id).firstOrNull()
    }

    fun listApps(): List<String> {
        val sql = "SELECT DISTINCT app_name FROM $table WHERE app_name IS NOT NULL ORDER BY app_name"
        return jdbcTemplate.queryForList(sql, String::class.java)
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    fun stats(startTime: String?, endTime: String?): ApiLogStats {
        val (whereClause, params) = buildWhere(
            ApiLogQuery(startTime = startTime, endTime = endTime),
        )

        val total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM $table $whereClause",
            Long::class.java,
            *params.toTypedArray(),
        ) ?: 0L

        val countByMethod = groupCount("method", whereClause, params)
            .mapKeys { it.key as String }

        @Suppress("UNCHECKED_CAST")
        val countByStatus = groupCount("response_status", whereClause, params)
            .mapKeys { (it.key as Number).toInt() }

        val countByAppName = groupCount("app_name", whereClause, params)
            .filterKeys { it != null }
            .mapKeys { it.key as String }

        val aggConnector = if (whereClause.isBlank()) "WHERE" else "AND"
        val aggSql = "SELECT AVG(processing_time_ms), MAX(processing_time_ms) FROM $table $whereClause $aggConnector processing_time_ms IS NOT NULL"
        val (avg, max) = jdbcTemplate.queryForObject(aggSql, { rs, _ ->
            (rs.getDouble(1)) to (rs.getLong(2))
        }, *params.toTypedArray()) ?: (0.0 to 0L)

        val p99 = calcPercentile(99, whereClause, params)

        return ApiLogStats(
            totalCount = total,
            countByMethod = countByMethod,
            countByStatus = countByStatus,
            countByAppName = countByAppName,
            avgProcessingTimeMs = avg,
            maxProcessingTimeMs = max,
            p99ProcessingTimeMs = p99,
        )
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun buildWhere(q: ApiLogQuery): Pair<String, List<Any>> {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        q.appName?.let { conditions += "app_name = ?"; params += it }
        q.method?.let { conditions += "UPPER(method) = UPPER(?)"; params += it }
        q.url?.let { conditions += "url LIKE CONCAT('%', ?, '%')"; params += it }
        q.statusCode?.let { code ->
            val range = parseStatusCodeFilter(code)
            if (range != null) {
                conditions += "response_status >= ? AND response_status < ?"
                params += range.first
                params += range.second
            } else {
                val exact = code.toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid statusCode: '$code'. Use exact code (e.g. 200) or class pattern (e.g. 2XX).")
                conditions += "response_status = ?"
                params += exact
            }
        }
        q.startTime?.let { conditions += "request_time >= ?"; params += parseDateTime(it) }
        q.endTime?.let { conditions += "request_time <= ?"; params += parseDateTime(it) }
        q.minProcessingTimeMs?.let { conditions += "processing_time_ms >= ?"; params += it }
        q.remoteAddr?.let { conditions += "remote_addr LIKE CONCAT('%', ?, '%')"; params += it }
        q.serverName?.let { conditions += "server_name LIKE CONCAT('%', ?, '%')"; params += it }

        val whereClause = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
        return whereClause to params
    }

    private fun groupCount(
        column: String,
        whereClause: String,
        params: List<Any>,
    ): Map<Any?, Long> {
        val sql = "SELECT $column, COUNT(*) FROM $table $whereClause GROUP BY $column"
        return jdbcTemplate.query(sql, { rs, _ ->
            rs.getObject(1) to (rs.getLong(2))
        }, *params.toTypedArray()).toMap()
    }

    /**
     * Calculates an approximate percentile using the offset/limit method.
     * This avoids DB-specific percentile functions (e.g., `PERCENTILE_CONT`).
     */
    private fun calcPercentile(pct: Int, whereClause: String, params: List<Any>): Long? {
        val connector = if (whereClause.isBlank()) "WHERE" else "AND"
        val notNullClause = "$whereClause $connector processing_time_ms IS NOT NULL"
        val countSql = "SELECT COUNT(*) FROM $table $notNullClause"
        val total = jdbcTemplate.queryForObject(countSql, Long::class.java, *params.toTypedArray()) ?: 0L
        if (total == 0L) return null
        val offset = ((total * pct / 100.0).toLong()).coerceIn(0, total - 1)
        val sql =
            "SELECT processing_time_ms FROM $table $notNullClause ORDER BY processing_time_ms LIMIT 1 OFFSET $offset"
        return jdbcTemplate.queryForObject(sql, Long::class.java, *params.toTypedArray())
    }

    private fun parseDateTime(value: String): LocalDateTime =
        runCatching { LocalDateTime.parse(value) }.getOrElse {
            throw IllegalArgumentException("Invalid datetime format: '$value'. Expected ISO-8601, e.g. 2026-01-15T00:00:00")
        }

    /**
     * Parses a status-code class pattern like `2XX`, `3XX`, `4XX`, `5XX` into a half-open range.
     * Returns `null` if the value is not a class pattern (treat as exact match).
     */
    private fun parseStatusCodeFilter(value: String): Pair<Int, Int>? {
        val upper = value.uppercase()
        val match = Regex("^([1-9])XX$").find(upper) ?: return null
        val base = match.groupValues[1].toInt() * 100
        return base to (base + 100)
    }

    @Suppress("UNCHECKED_CAST")
    private fun rowMapper(): RowMapper<ApiLogEntry> = RowMapper { rs: ResultSet, _ ->
        ApiLogEntry(
            id = rs.getString("id"),
            appName = rs.getString("app_name"),
            url = rs.getString("url"),
            method = rs.getString("method"),
            queryParams = rs.getString("query_params")?.jsonToClass<Map<String, List<String>>>() ?: emptyMap(),
            requestHeaders = rs.getString("request_headers")?.jsonToClass<Map<String, String>>() ?: emptyMap(),
            requestBody = rs.getString("request_body"),
            responseStatus = rs.getObject("response_status") as? Int,
            responseContentType = rs.getString("response_content_type"),
            responseBody = rs.getString("response_body"),
            requestTime = rs.getTimestamp("request_time").toLocalDateTime(),
            responseTime = rs.getTimestamp("response_time")?.toLocalDateTime(),
            processingTimeMs = rs.getObject("processing_time_ms") as? Long,
            serverName = rs.getString("server_name"),
            serverPort = rs.getObject("server_port") as? Int,
            remoteAddr = rs.getString("remote_addr"),
        )
    }
}
