package com.hshim.springapiassist.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "api-assist.document")
class DocumentProperties {

    var enabled: Boolean = true
    var view: ViewDetail = ViewDetail()

    class ViewDetail(
        var basePath: String = "/api/documents"
    )
}