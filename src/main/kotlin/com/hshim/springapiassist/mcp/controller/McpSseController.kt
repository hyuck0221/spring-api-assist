package com.hshim.springapiassist.mcp.controller

import com.hshim.springapiassist.configuration.properties.McpProperties
import com.hshim.springapiassist.mcp.service.McpService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import util.ClassUtil.classToJson
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@RestController
@RequestMapping("\${api-assist.mcp.transport.sse.base-path:/mcp/sse}")
@CrossOrigin(origins = ["*"])
@ConditionalOnProperty(prefix = "api-assist.mcp.transport.sse", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class McpSseController(
    private val mcpService: McpService,
    private val mcpProperties: McpProperties,
) {
    private val logger = LoggerFactory.getLogger(McpSseController::class.java)

    private val emitters = ConcurrentHashMap<String, SseEmitter>()

    @EventListener(ContextClosedEvent::class)
    fun onShutdown() {
        logger.info("Closing all SSE emitters due to shutdown...")
        emitters.forEach { (_, emitter) ->
            try {
                emitter.complete()
            } catch (_: Exception) {
            }
        }
        emitters.clear()
    }

    @GetMapping
    fun connectSse(): SseEmitter {
        val sessionId = UUID.randomUUID().toString()
        val emitter = SseEmitter(Long.MAX_VALUE)

        emitters[sessionId] = emitter

        emitter.onCompletion {
            logger.info("SSE Completed: $sessionId")
            emitters.remove(sessionId)
        }
        emitter.onTimeout {
            logger.info("SSE Timeout: $sessionId")
            emitters.remove(sessionId)
        }
        emitter.onError {
            logger.error("SSE Error: $sessionId")
            emitters.remove(sessionId)
        }

        try {
            val basePath = mcpProperties.transport.sse.basePath
            emitter.send(SseEmitter.event().name("endpoint").data("$basePath/message?sessionId=$sessionId"))
            logger.info("SSE Connected: $sessionId")
        } catch (_: Exception) {
            emitters.remove(sessionId)
        }

        return emitter
    }

    @PostMapping("/message")
    fun handleMessage(
        @RequestParam sessionId: String,
        @RequestBody requestBody: Map<String, Any>
    ) {
        val emitter = emitters[sessionId] ?: run {
            logger.warn("Session not found: $sessionId")
            throw IllegalArgumentException("Session not found")
        }

        try {
            val response = mcpService.handleMessage(requestBody)
            if (response != null) {
                emitter.send(SseEmitter.event().name("message").data(response.classToJson()))
            }
        } catch (e: Exception) {
            logger.error("Error handling message for session $sessionId", e)
            try {
                val errorRes = mapOf(
                    "jsonrpc" to "2.0",
                    "id" to requestBody["id"],
                    "error" to mapOf("code" to -32603, "message" to "Internal error: ${e.message}")
                )
                emitter.send(SseEmitter.event().name("message").data(errorRes.classToJson()))
            } catch (_: Exception) {
            }
        }
    }
}
