package com.hshim.springapiassist.configuration

import com.hshim.springapiassist.configuration.properties.McpProperties
import com.hshim.springapiassist.document.component.APIDocumentComponent
import com.hshim.springapiassist.mcp.controller.McpSseController
import com.hshim.springapiassist.mcp.service.McpService
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

@AutoConfiguration(after = [ApiDocumentAutoConfiguration::class])
@ConditionalOnProperty(prefix = "api-assist.mcp", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(McpProperties::class)
@ComponentScan(basePackageClasses = [McpService::class, McpSseController::class])
class McpAutoConfiguration {

    /**
     * api-assist.document.enabled=false 여도 MCP는 APIDocumentComponent가 필요하므로
     * 없을 경우 여기서 직접 생성
     */
    @Bean
    @ConditionalOnMissingBean
    fun apiDocumentComponent(handlerMapping: RequestMappingHandlerMapping): APIDocumentComponent =
        APIDocumentComponent(handlerMapping)
}