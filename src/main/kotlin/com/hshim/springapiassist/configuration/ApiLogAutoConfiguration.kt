package com.hshim.springapiassist.configuration

import com.hshim.springapiassist.log.filter.ApiLogFilter
import com.hshim.springapiassist.log.storage.ApiLogStorage
import com.hshim.springapiassist.log.storage.db.ApiLogDbStorage
import com.hshim.springapiassist.log.storage.local.ApiLogLocalFileStorage
import com.hshim.springapiassist.log.storage.supabase.ApiLogSupabaseDbStorage
import com.hshim.springapiassist.log.storage.supabase.ApiLogSupabaseS3Storage
import com.hshim.springapiassist.log.view.controller.ApiLogFileController
import com.hshim.springapiassist.log.view.controller.ApiLogViewController
import com.hshim.springapiassist.log.view.filter.ApiLogViewAuthFilter
import com.hshim.springapiassist.log.view.service.ApiLogFileService
import com.hshim.springapiassist.log.view.service.ApiLogViewService
import com.hshim.springapiassist.configuration.properties.ApiAssistProperties
import com.hshim.springapiassist.configuration.properties.ApiLogProperties
import com.hshim.springapiassist.configuration.properties.DocumentProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.jdbc.core.JdbcTemplate

@AutoConfiguration(after = [JdbcTemplateAutoConfiguration::class])
@ConditionalOnWebApplication
@EnableConfigurationProperties(ApiAssistProperties::class, ApiLogProperties::class)
@ConditionalOnProperty(prefix = "api-assist.log", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class ApiLogAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "api-assist.log.storage.local-file", name = ["enabled"], havingValue = "true")
    fun apiLogLocalFileStorage(
        properties: ApiLogProperties,
    ): ApiLogLocalFileStorage = ApiLogLocalFileStorage(properties)

    @Bean
    @ConditionalOnProperty(prefix = "api-assist.log.storage.supabase-db", name = ["enabled"], havingValue = "true")
    fun apiLogSupabaseDbStorage(
        properties: ApiLogProperties,
    ): ApiLogSupabaseDbStorage = ApiLogSupabaseDbStorage(properties)

    @Bean
    @ConditionalOnProperty(prefix = "api-assist.log.storage.supabase-s3", name = ["enabled"], havingValue = "true")
    fun apiLogSupabaseS3Storage(
        properties: ApiLogProperties,
    ): ApiLogSupabaseS3Storage = ApiLogSupabaseS3Storage(properties)

    @Bean
    fun apiLogFilter(
        properties: ApiLogProperties,
        documentProperties: DocumentProperties,
        storagesProvider: ObjectProvider<ApiLogStorage>,
    ): ApiLogFilter = ApiLogFilter(
        properties = properties,
        storages = storagesProvider.orderedStream().toList(),
        internalExcludePaths = listOf(
            "${properties.view.basePath}/**",
            "${documentProperties.view.basePath}/**",
        ),
    )

    @Bean
    fun apiLogFilterRegistration(apiLogFilter: ApiLogFilter): FilterRegistrationBean<ApiLogFilter> =
        FilterRegistrationBean(apiLogFilter).apply {
            order = Ordered.LOWEST_PRECEDENCE - 10
            addUrlPatterns("/*")
        }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcTemplate::class)
    @ConditionalOnProperty(prefix = "api-assist.log.storage.db", name = ["enabled"], havingValue = "true")
    class DbStorageConfiguration {

        @Bean
        @ConditionalOnBean(JdbcTemplate::class)
        fun apiLogDbStorage(
            jdbcTemplate: JdbcTemplate,
            properties: ApiLogProperties,
        ) = ApiLogDbStorage(jdbcTemplate, properties)
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "api-assist.log.view", name = ["enabled"], havingValue = "true")
    class FileConfiguration {

        @Bean
        @ConditionalOnMissingBean
        fun apiLogFileService() = ApiLogFileService()

        @Bean
        @ConditionalOnMissingBean
        fun apiLogFileController(fileService: ApiLogFileService) = ApiLogFileController(fileService)
    }


    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcTemplate::class)
    @ConditionalOnProperty(prefix = "api-assist.log.view", name = ["enabled"], havingValue = "true")
    class ViewConfiguration {

        @Bean
        @ConditionalOnBean(JdbcTemplate::class)
        fun apiLogViewService(
            jdbcTemplate: JdbcTemplate,
            properties: ApiLogProperties,
        ): ApiLogViewService = ApiLogViewService(jdbcTemplate, properties)

        @Bean
        @ConditionalOnBean(ApiLogViewService::class)
        @ConditionalOnMissingBean
        fun apiLogViewController(
            viewService: ApiLogViewService,
            storagesProvider: ObjectProvider<ApiLogStorage>,
        ): ApiLogViewController = ApiLogViewController(viewService, storagesProvider)

        @Bean
        @ConditionalOnProperty(prefix = "api-assist", name = ["api-key"], matchIfMissing = false)
        fun apiLogViewAuthFilterRegistration(
            apiAssistProperties: ApiAssistProperties,
            properties: ApiLogProperties,
            documentProperties: DocumentProperties,
        ): FilterRegistrationBean<ApiLogViewAuthFilter> =
            FilterRegistrationBean(ApiLogViewAuthFilter(apiAssistProperties)).apply {
                order = Ordered.HIGHEST_PRECEDENCE
                addUrlPatterns("${properties.view.basePath}/*")
                addUrlPatterns("${documentProperties.view.basePath}/*")
            }
    }
}