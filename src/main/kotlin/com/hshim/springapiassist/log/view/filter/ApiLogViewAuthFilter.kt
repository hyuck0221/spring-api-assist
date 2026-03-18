package com.hshim.springapiassist.log.view.filter

import com.hshim.springapiassist.configuration.properties.ApiAssistProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

class ApiLogViewAuthFilter(private val properties: ApiAssistProperties) : OncePerRequestFilter() {

    companion object {
        const val HEADER_NAME = "X-Api-Key"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val expectedKey = properties.apiKey.trim()

        if (expectedKey.isBlank()) {
            filterChain.doFilter(request, response)
            return
        }

        val providedKey = request.getHeader(HEADER_NAME)?.trim()

        if (providedKey != expectedKey) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json;charset=UTF-8"
            response.writer.write(
                """{"status":401,"error":"Unauthorized","message":"Invalid or missing $HEADER_NAME header"}""",
            )
            return
        }

        filterChain.doFilter(request, response)
    }
}
