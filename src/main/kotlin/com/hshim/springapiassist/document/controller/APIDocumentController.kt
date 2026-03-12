package com.hshim.springapiassist.document.controller

import com.hshim.springapiassist.document.component.APIDocumentComponent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.*

@RestController
@CrossOrigin(origins = ["*"])
@RequestMapping("\${api-assist.document.view.base-path:/api/documents}")
@ConditionalOnProperty(prefix = "api-assist.document", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class APIDocumentController(private val documentComponent: APIDocumentComponent) {

    @GetMapping("/status")
    fun status() = mapOf("enabled" to true)

    @GetMapping("/categories")
    fun categories() = documentComponent.getCategories()

    @GetMapping
    fun list(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) method: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ) = documentComponent.findAllPageBy(keyword, category, method, page, size)
}