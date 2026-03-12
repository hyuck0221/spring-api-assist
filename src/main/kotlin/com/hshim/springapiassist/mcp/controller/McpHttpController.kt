package com.hshim.springapiassist.mcp.controller

import com.hshim.springapiassist.mcp.service.McpService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${api-assist.mcp.transport.http.base-path:/mcp}")
@CrossOrigin(origins = ["*"])
@ConditionalOnProperty(
    prefix = "api-assist.mcp.transport.http",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class McpHttpController(private val mcpService: McpService) {

    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun handleMessage(@RequestBody(required = false) requestBody: Map<String, Any>?): ResponseEntity<Any> {
        if (requestBody == null) {
            return ResponseEntity.badRequest().body(
                mapOf(
                    "jsonrpc" to "2.0",
                    "id" to null,
                    "error" to mapOf("code" to -32700, "message" to "Parse error: request body is required")
                )
            )
        }

        return mcpService.handleMessage(requestBody)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.accepted().build()
    }
}
