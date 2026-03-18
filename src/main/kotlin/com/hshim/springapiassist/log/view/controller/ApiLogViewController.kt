package com.hshim.springapiassist.log.view.controller

import com.hshim.springapiassist.log.model.ApiLogEntry
import com.hshim.springapiassist.log.storage.ApiLogStorage
import com.hshim.springapiassist.log.view.dto.ApiLogPage
import com.hshim.springapiassist.log.view.dto.ApiLogQuery
import com.hshim.springapiassist.log.view.dto.ApiLogStats
import com.hshim.springapiassist.log.view.service.ApiLogViewService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.RestController

@RestController
@CrossOrigin(origins = ["*"])
@RequestMapping("\${api-assist.log.view.base-path:/api/logs}")
@ConditionalOnProperty(prefix = "api-assist.log.view", name = ["enabled"], havingValue = "true")
class ApiLogViewController(
    private val viewService: ApiLogViewService,
    private val storagesProvider: ObjectProvider<ApiLogStorage>,
) {
    @PostMapping("/receive")
    fun receive(@RequestBody entry: ApiLogEntry): ResponseEntity<Void> {
        storagesProvider.orderedStream().forEach { it.save(entry) }
        return ResponseEntity.accepted().build()
    }

    @GetMapping
    fun list(
        @RequestParam(required = false) appName: String?,
        @RequestParam(required = false) method: String?,
        @RequestParam(required = false) url: String?,
        @RequestParam(required = false) statusCode: String?,
        @RequestParam(required = false) startTime: String?,
        @RequestParam(required = false) endTime: String?,
        @RequestParam(required = false) minProcessingTimeMs: Long?,
        @RequestParam(required = false) remoteAddr: String?,
        @RequestParam(required = false) serverName: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "request_time") sortBy: String,
        @RequestParam(defaultValue = "DESC") sortDir: String,
    ): ApiLogPage = viewService.queryLogs(
        ApiLogQuery(
            appName = appName,
            method = method,
            url = url,
            statusCode = statusCode,
            startTime = startTime,
            endTime = endTime,
            minProcessingTimeMs = minProcessingTimeMs,
            remoteAddr = remoteAddr,
            serverName = serverName,
            page = page,
            size = size,
            sortBy = sortBy,
            sortDir = sortDir,
        ),
    )

    @GetMapping("/{id}")
    fun getById(@PathVariable id: String): ResponseEntity<ApiLogEntry> {
        val entry = viewService.findById(id)
        return if (entry != null) ResponseEntity.ok(entry) else ResponseEntity.notFound().build()
    }

    @GetMapping("/stats")
    fun stats(
        @RequestParam(required = false) startTime: String?,
        @RequestParam(required = false) endTime: String?,
    ): ApiLogStats = viewService.stats(startTime, endTime)

    @GetMapping("/apps")
    fun listApps(): List<String> = viewService.listApps()
}
