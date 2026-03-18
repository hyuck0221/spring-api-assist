package com.hshim.springapiassist.log.filter

import com.hshim.springapiassist.log.storage.ApiLogStorage
import com.hshim.springapiassist.configuration.properties.ApiLogProperties
import com.hshim.springapiassist.log.model.ApiLogEntry
import util.ClassUtil.classToJson
import util.ClassUtil.jsonToClass
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter
import java.time.LocalDateTime

class ApiLogFilter(
    private val properties: ApiLogProperties,
    private val storages: List<ApiLogStorage>,
    private val internalExcludePaths: List<String> = emptyList(),
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(ApiLogFilter::class.java)
    private val pathMatcher = AntPathMatcher()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!properties.enabled || !isIncluded(request.requestURI) || isExcluded(request.requestURI)) {
            filterChain.doFilter(request, response)
            return
        }

        if (isStreamRequest(request)) {
            doFilterForStream(request, response, filterChain)
            return
        }

        val wrappedRequest = CachingRequestWrapper(request)
        val wrappedResponse = CachingResponseWrapper(response)
        val requestTime = LocalDateTime.now()
        val startNanos = System.nanoTime()

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse)
        } finally {
            val processingTimeMs = (System.nanoTime() - startNanos) / 1_000_000L
            val responseTime = LocalDateTime.now()

            try {
                val entry = buildLogEntry(wrappedRequest, wrappedResponse, requestTime, responseTime, processingTimeMs)
                storages.forEach { storage ->
                    try {
                        storage.save(entry)
                    } catch (e: Exception) {
                        log.error("Failed to save API log via ${storage::class.simpleName}", e)
                    }
                }
            } catch (e: Exception) {
                log.error("Failed to build API log entry for ${request.requestURI}", e)
            } finally {
                wrappedResponse.copyBodyToResponse()
            }
        }
    }

    private fun doFilterForStream(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val wrappedRequest = CachingRequestWrapper(request)
        val requestTime = LocalDateTime.now()

        try {
            val entry = buildRequestOnlyEntry(wrappedRequest, requestTime)
            storages.forEach { storage ->
                try {
                    storage.save(entry)
                } catch (e: Exception) {
                    log.error("Failed to save stream API log via ${storage::class.simpleName}", e)
                }
            }
        } catch (e: Exception) {
            log.error("Failed to build stream API log entry for ${request.requestURI}", e)
        }

        filterChain.doFilter(wrappedRequest, response)
    }

    private fun isIncluded(uri: String): Boolean {
        if (properties.includePaths.isEmpty()) return true
        return properties.includePaths.any { pattern -> pathMatcher.match(pattern, uri) }
    }

    private fun isExcluded(uri: String): Boolean =
        internalExcludePaths.any { pattern -> pathMatcher.match(pattern, uri) } ||
                properties.excludePaths.any { pattern -> pathMatcher.match(pattern, uri) }

    private fun isStreamRequest(request: HttpServletRequest): Boolean {
        val accept = request.getHeader("Accept") ?: ""
        val contentType = request.contentType ?: ""
        val upgrade = request.getHeader("Upgrade") ?: ""
        return accept.contains("text/event-stream", ignoreCase = true) ||
                contentType.contains("text/event-stream", ignoreCase = true) ||
                upgrade.equals("websocket", ignoreCase = true)
    }

    private fun buildRequestOnlyEntry(
        request: CachingRequestWrapper,
        requestTime: LocalDateTime,
    ): ApiLogEntry {
        val requestHeaders: Map<String, String> = request.headerNames.asSequence()
            .associateWith { headerName ->
                if (properties.maskHeaders.any { it.equals(headerName, ignoreCase = true) }) "***"
                else request.getHeader(headerName) ?: ""
            }

        val requestBody = extractBody(
            bytes = request.cachedBody,
            encoding = request.characterEncoding,
            contentType = request.contentType,
            masked = properties.maskRequestBody,
            maskFields = properties.maskRequestFields,
            maxSize = properties.maxBodySize,
        )

        return ApiLogEntry(
            appName = properties.appName.ifBlank { null },
            url = request.requestURI,
            method = request.method,
            queryParams = request.parameterMap.mapValues { it.value.toList() },
            requestHeaders = requestHeaders,
            requestBody = requestBody,
            requestTime = requestTime,
            serverName = request.serverName,
            serverPort = request.serverPort,
            remoteAddr = request.remoteAddr,
        )
    }

    private fun buildLogEntry(
        request: CachingRequestWrapper,
        response: CachingResponseWrapper,
        requestTime: LocalDateTime,
        responseTime: LocalDateTime,
        processingTimeMs: Long,
    ): ApiLogEntry {
        val requestHeaders: Map<String, String> = request.headerNames.asSequence()
            .associateWith { headerName ->
                if (properties.maskHeaders.any { it.equals(headerName, ignoreCase = true) }) "***"
                else request.getHeader(headerName) ?: ""
            }

        val requestBody = extractBody(
            bytes = request.cachedBody,
            encoding = request.characterEncoding,
            contentType = request.contentType,
            masked = properties.maskRequestBody,
            maskFields = properties.maskRequestFields,
            maxSize = properties.maxBodySize,
        )

        val responseBody = extractBody(
            bytes = response.cachedBody,
            encoding = response.characterEncoding,
            contentType = response.contentType,
            masked = properties.maskResponseBody,
            maskFields = properties.maskResponseFields,
            maxSize = properties.maxBodySize,
        )

        return ApiLogEntry(
            appName = properties.appName.ifBlank { null },
            url = request.requestURI,
            method = request.method,
            queryParams = request.parameterMap.mapValues { it.value.toList() },
            requestHeaders = requestHeaders,
            requestBody = requestBody,
            responseStatus = response.status,
            responseContentType = response.contentType,
            responseBody = responseBody,
            requestTime = requestTime,
            responseTime = responseTime,
            processingTimeMs = processingTimeMs,
            serverName = request.serverName,
            serverPort = request.serverPort,
            remoteAddr = request.remoteAddr,
        )
    }

    private fun extractBody(
        bytes: ByteArray,
        encoding: String?,
        contentType: String?,
        masked: Boolean,
        maskFields: List<String>,
        maxSize: Int,
    ): String? {
        if (bytes.isEmpty()) return null
        if (masked) return "***"
        val charset = encoding
            ?.takeIf { it.isNotBlank() && !it.equals("ISO-8859-1", ignoreCase = true) }
            ?.let { runCatching { charset(it) }.getOrNull() }
            ?: Charsets.UTF_8
        var body = String(bytes, charset)
        if (maskFields.isNotEmpty() && contentType?.contains("application/json", ignoreCase = true) == true) {
            body = maskJsonFields(body, maskFields)
        }
        return if (body.length > maxSize) "${body.substring(0, maxSize)}...[truncated]" else body
    }

    private fun maskJsonFields(body: String, fields: List<String>): String {
        return try {
            val root = body.jsonToClass<Any>()
            when (root) {
                is MutableMap<*, *> -> @Suppress("UNCHECKED_CAST") maskMap(root as MutableMap<String, Any?>, fields)
                is List<*> -> root.filterIsInstance<MutableMap<String, Any?>>().forEach { maskMap(it, fields) }
            }
            root.classToJson()
        } catch (e: Exception) {
            body
        }
    }

    private fun maskMap(map: MutableMap<String, Any?>, fields: List<String>) {
        val flatFields = mutableListOf<String>()
        for (field in fields) {
            val dotIdx = field.indexOf('.')
            if (dotIdx == -1) {
                if (map.containsKey(field)) map[field] = "***"
                flatFields += field
            } else {
                val head = field.substring(0, dotIdx)
                val tail = field.substring(dotIdx + 1)
                @Suppress("UNCHECKED_CAST")
                when (val child = map[head]) {
                    is MutableMap<*, *> -> maskMap(child as MutableMap<String, Any?>, listOf(tail))
                    is List<*> -> child.filterIsInstance<MutableMap<String, Any?>>().forEach { maskMap(it, listOf(tail)) }
                }
            }
        }
        if (flatFields.isNotEmpty()) {
            map.values.forEach { child ->
                @Suppress("UNCHECKED_CAST")
                when (child) {
                    is MutableMap<*, *> -> maskMap(child as MutableMap<String, Any?>, flatFields)
                    is List<*> -> child.filterIsInstance<MutableMap<String, Any?>>().forEach { maskMap(it, flatFields) }
                }
            }
        }
    }
}
