package com.hshim.springapiassist.log.view.controller

import com.hshim.springapiassist.log.view.dto.ApiLogFileInfo
import com.hshim.springapiassist.log.view.service.ApiLogFileService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.*
import java.io.FileNotFoundException

@RestController
@CrossOrigin(origins = ["*"])
@RequestMapping("\${api-assist.log.view.base-path:/api/logs}/files")
@ConditionalOnProperty(prefix = "api-assist.log.view", name = ["enabled"], havingValue = "true")
class ApiLogFileController(private val fileService: ApiLogFileService) {

    @GetMapping
    fun listFiles(
        @RequestParam(defaultValue = "logs/") directory: String,
        @RequestParam(defaultValue = "5") maxFiles: Int,
        @RequestParam(defaultValue = "json") format: String,
    ): List<ApiLogFileInfo> = fileService.listFiles(directory, maxFiles, format)

    @GetMapping("/content")
    fun fileContent(
        @RequestParam path: String,
        @RequestParam(defaultValue = "logs/") directory: String,
    ): ResponseEntity<String> {
        return try {
            val content = fileService.readFileContent(path, directory)
            ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(content)
        } catch (e: FileNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (e: SecurityException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.message)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(e.message)
        }
    }
}
