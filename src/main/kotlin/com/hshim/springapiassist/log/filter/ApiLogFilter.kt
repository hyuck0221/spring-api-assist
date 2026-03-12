package com.hshim.springapiassist.log.filter

import com.hshim.springapiassist.log.storage.ApiLogStorage
import com.hshim.springapiassist.configuration.properties.ApiLogProperties
import com.hshim.springapiassist.log.model.ApiLogEntry
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
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(ApiLogFilter::class.java)
    private val pathMatcher = AntPathMatcher()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!properties.enabled || !isIncluded(request.requestURI) || isExcluded(request.requestURI) || isStreamRequest(request)) {
            filterChain.doFilter(request, response)
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

    private fun isIncluded(uri: String): Boolean {
        if (properties.includePaths.isEmpty()) return true
        return properties.includePaths.any { pattern -> pathMatcher.match(pattern, uri) }
    }

    private fun isExcluded(uri: String): Boolean =
        properties.excludePaths.any { pattern -> pathMatcher.match(pattern, uri) }

    private fun isStreamRequest(request: HttpServletRequest): Boolean {
        val accept = request.getHeader("Accept") ?: ""
        val contentType = request.contentType ?: ""
        return accept.contains("text/event-stream", ignoreCase = true) ||
                contentType.contains("text/event-stream", ignoreCase = true)
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
            masked = properties.maskRequestBody,
            maxSize = properties.maxBodySize,
        )

        val responseBody = extractBody(
            bytes = response.cachedBody,
            encoding = response.characterEncoding,
            masked = properties.maskResponseBody,
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
        masked: Boolean,
        maxSize: Int,
    ): String? {
        if (bytes.isEmpty()) return null
        if (masked) return "***"
        val charset = encoding?.let { runCatching { charset(it) }.getOrNull() } ?: Charsets.UTF_8
        val body = String(bytes, charset)
        return if (body.length > maxSize) "${body.substring(0, maxSize)}...[truncated]" else body
    }
}
