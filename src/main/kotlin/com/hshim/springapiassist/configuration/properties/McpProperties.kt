package com.hshim.springapiassist.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "api-assist.mcp")
class McpProperties {

    var enabled: Boolean = true
    var serverInfo = ServerInfo()
    var transport = Transport()

    class ServerInfo {
        var name: String = "Spring API Docs MCP Server"
        var version: String = "1.0.0"
    }

    class Transport {
        var sse: TransportDetail = TransportDetail(basePath = "/mcp/sse")
        var http: TransportDetail = TransportDetail(basePath = "/mcp")
    }

    class TransportDetail(
        var enabled: Boolean = true,
        var basePath: String,
    )
}