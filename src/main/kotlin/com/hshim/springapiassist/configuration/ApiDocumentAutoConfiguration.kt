package com.hshim.springapiassist.configuration

import com.hshim.springapiassist.configuration.properties.DocumentProperties
import com.hshim.springapiassist.document.component.APIDocumentComponent
import com.hshim.springapiassist.document.controller.APIDocumentController
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

@AutoConfiguration
@ConditionalOnProperty(prefix = "api-assist.document", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DocumentProperties::class)
class ApiDocumentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun apiDocumentComponent(handlerMapping: RequestMappingHandlerMapping) = APIDocumentComponent(handlerMapping)

    @Bean
    @ConditionalOnMissingBean
    fun apiDocumentController(documentComponent: APIDocumentComponent) = APIDocumentController(documentComponent)
}