# spring-api-assist

A Spring Boot library that automatically adds **API Logging**, **API Documentation**, and **MCP Server** capabilities to your application.

---

## Features

| Feature | Description                                                                                 |
|---------|---------------------------------------------------------------------------------------------|
| **API Logging** | Automatically captures all HTTP requests/responses and stores them in DB, file, or Supabase |
| **API Documentation** | Auto-scans Spring MVC endpoints and provides an API catalog                                 |
| **MCP Server** | Exposes API information via Model Context Protocol for AI clients like Claude               |
| **Log Viewer** | REST API for querying and searching stored logs ([viewer](https://github.com/hyuck0221/apilog-view))                                            |

---

## Installation

### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.hyuck0221:spring-api-assist:0.0.3")
}
```

### Gradle (Groovy)

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.hyuck0221:spring-api-assist:0.0.3'
}
```

---

## Quick Start

Add the following to your `application.yml`. All settings are optional and have sensible defaults.

```yaml
api-assist:
  log:
    enabled: true
    app-name: my-app

  document:
    enabled: true

  mcp:
    enabled: true
```

---

## API Logging

### How It Works

A servlet filter captures all incoming HTTP requests and outgoing responses. Captured logs are persisted to all enabled storage backends simultaneously.

### Log Entry Fields (ApiLogEntry)

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | ULID unique identifier |
| `appName` | String? | Configured application name |
| `url` | String | Request URL |
| `method` | String | HTTP method |
| `queryParams` | Map | Query parameters |
| `requestHeaders` | Map | Request headers (masking applied) |
| `requestBody` | String? | Request body |
| `responseStatus` | Int | HTTP status code |
| `responseContentType` | String? | Response Content-Type |
| `responseBody` | String? | Response body |
| `requestTime` | LocalDateTime | Request start time |
| `responseTime` | LocalDateTime | Response completion time |
| `processingTimeMs` | Long | Processing duration (ms) |
| `serverName` | String? | Server hostname |
| `serverPort` | Int? | Server port |
| `remoteAddr` | String? | Client IP address |

### Configuration

```yaml
api-assist:
  log:
    enabled: true                  # Enable logging (default: true)
    app-name: my-app               # App identifier in logs (default: "")
    include-paths:                 # Paths to log (default: all)
      - /**
    exclude-paths:                 # Paths to exclude
      - /swagger-ui/**
      - /actuator/**
    mask-headers:                  # Headers to mask (default: Authorization, Cookie, Set-Cookie)
      - Authorization
      - Cookie
      - Set-Cookie
    mask-request-body: false       # Mask request body (default: false)
    mask-response-body: false      # Mask response body (default: false)
    max-body-size: 10000           # Max body capture size in bytes (default: 10000)
```

> SSE (`text/event-stream`) requests are automatically excluded from logging.

---

## Storage

### 1. Database (Spring JDBC)

Shares the application's existing DataSource. Requires `spring-boot-starter-jdbc`.

```yaml
api-assist:
  log:
    storage:
      db:
        enabled: true
        table-name: api_logs        # Table name (default: api_logs)
        auto-create-table: true     # Auto-create table on startup (default: true)
```

### 2. Local File

Writes to the local filesystem in JSON or CSV format.

```yaml
api-assist:
  log:
    storage:
      local-file:
        enabled: true
        path: ./logs/api            # Storage directory (default: ./logs/api)
        logs-per-file: 1000         # Max entries per file (default: 1000)
        flush-interval-seconds: 0   # Periodic flush interval (0 = on buffer full only)
        format: JSON                # JSON or CSV (default: JSON)
```

### 3. Supabase DB (PostgreSQL)

Stores logs in a dedicated Supabase PostgreSQL instance.

```yaml
api-assist:
  log:
    storage:
      supabase-db:
        enabled: true
        jdbc-url: jdbc:postgresql://db.xxxxx.supabase.co:5432/postgres
        username: postgres
        password: your-password
        table-name: api_logs
        auto-create-table: true
```

### 4. Supabase S3 (S3-Compatible Storage)

Stores log files in Supabase Storage (S3-compatible).

```yaml
api-assist:
  log:
    storage:
      supabase-s3:
        enabled: true
        endpoint-url: https://xxxxx.supabase.co/storage/v1/s3
        access-key-id: your-access-key
        secret-access-key: your-secret-key
        region: ap-northeast-1
        bucket: api-logs
        key-prefix: logs/
        logs-per-file: 1000
        format: JSON                # JSON or CSV
```

---

## API Documentation

Auto-scans Spring MVC controllers to provide an API catalog. Supports both OpenAPI 3.x (`@Operation`, `@Tag`) and Swagger 2.x (`@ApiOperation`, `@Api`) annotations.

### Configuration

```yaml
api-assist:
  document:
    enabled: true                          # Enable (default: true)
    view:
      base-path: /api/documents            # Endpoint base path (default: /api/documents)
```

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/documents/status` | Check if documentation is enabled |
| `GET` | `/api/documents/categories` | List all API categories |
| `GET` | `/api/documents` | List APIs with pagination and filtering |

#### `GET /api/documents` Query Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `keyword` | - | Search by keyword (URL, title, description) |
| `category` | - | Filter by category |
| `method` | - | Filter by HTTP method |
| `page` | `0` | Page number |
| `size` | `20` | Page size |

---

## MCP Server

Allows MCP-compatible AI clients (e.g., Claude) to query your application's API information in real time.

### Configuration

```yaml
api-assist:
  mcp:
    enabled: true
    server-info:
      name: My App MCP Server      # MCP server name
      version: 1.0.0               # MCP server version
    transport:
      sse:
        enabled: true
        base-path: /mcp/sse        # SSE endpoint (default: /mcp/sse)
      http:
        enabled: true
        base-path: /mcp            # HTTP endpoint (default: /mcp)
```

### Available MCP Tools

| Tool | Description | Parameters |
|------|-------------|------------|
| `getApiCount` | Get total API count | `category` (optional) |
| `getApiDetail` | Get details of a specific API | `url` (required), `method` (required) |
| `searchApis` | Search APIs | `keyword`, `category`, `method` (all optional) |

### Claude Desktop Integration Example

```json
{
  "mcpServers": {
    "my-app": {
      "url": "http://localhost:8080/mcp/sse/sse",
      "transport": "sse"
    }
  }
}
```

---

## Log Viewer API

Query stored logs via REST API. **Disabled by default.** Requires a database storage backend.

### Configuration

```yaml
api-assist:
  api-key: your-secret-key         # Optional API key authentication
  log:
    view:
      enabled: true
      base-path: /api/logs         # Default: /api/logs
```

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/logs` | Query logs with pagination and filtering |
| `GET` | `/api/logs/{id}` | Get a specific log entry |
| `GET` | `/api/logs/stats` | Get log statistics |
| `GET` | `/api/logs/apps` | List all app names |
| `POST` | `/api/logs/receive` | Receive external log entries |
| `GET` | `/api/logs/files` | List log files |
| `GET` | `/api/logs/files/content` | Read log file content |

#### `GET /api/logs` Query Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `appName` | - | Filter by app name |
| `method` | - | Filter by HTTP method |
| `url` | - | Filter by URL |
| `statusCode` | - | Filter by status code |
| `startTime` | - | Start time filter |
| `endTime` | - | End time filter |
| `minProcessingTimeMs` | - | Minimum processing time (ms) |
| `page` | `0` | Page number |
| `size` | `20` | Page size |
| `sortBy` | `request_time` | Sort field |
| `sortDir` | `DESC` | Sort direction |

---

## Full Configuration Reference

```yaml
api-assist:
  api-key: your-secret-key

  document:
    enabled: true
    view:
      base-path: /api/documents

  log:
    enabled: true
    app-name: my-app
    include-paths:
      - /**
    exclude-paths:
      - /swagger-ui/**
      - /actuator/**
    mask-headers:
      - Authorization
      - Cookie
      - Set-Cookie
    mask-request-body: false
    mask-response-body: false
    max-body-size: 10000
    storage:
      db:
        enabled: true
        table-name: api_logs
        auto-create-table: true
      local-file:
        enabled: false
        path: ./logs/api
        logs-per-file: 1000
        flush-interval-seconds: 0
        format: JSON
      supabase-db:
        enabled: false
        jdbc-url: jdbc:postgresql://db.xxxxx.supabase.co:5432/postgres
        username: postgres
        password: your-password
        table-name: api_logs
        auto-create-table: true
      supabase-s3:
        enabled: false
        endpoint-url: https://xxxxx.supabase.co/storage/v1/s3
        access-key-id: your-key
        secret-access-key: your-secret
        region: ap-northeast-1
        bucket: api-logs
        key-prefix: logs/
        logs-per-file: 1000
        format: JSON
    view:
      enabled: true
      base-path: /api/logs

  mcp:
    enabled: true
    server-info:
      name: My App MCP Server
      version: 1.0.0
    transport:
      sse:
        enabled: true
        base-path: /mcp/sse
      http:
        enabled: true
        base-path: /mcp
```

---

## Requirements

| Requirement | Version |
|-------------|---------|
| Java | 17+ |
| Spring Boot | 3.x |
| Kotlin | Optional (Java supported) |
| spring-boot-starter-jdbc | Required for DB storage |
