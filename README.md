# spring-api-assist

[English](./readme-en.md)

Spring Boot 애플리케이션에 **API 로깅**, **API 문서화**, **MCP 서버** 기능을 자동으로 추가해주는 라이브러리입니다.

---

## 주요 기능

| 기능 | 설명                                                                                   |
|------|--------------------------------------------------------------------------------------|
| **API 로깅** | 모든 HTTP 요청/응답을 자동으로 캡처하여 DB, 파일, Supabase 등에 저장                                      |
| **API 문서화** | Spring MVC 엔드포인트를 자동 스캔하여 API 카탈로그 제공                                                |
| **MCP 서버** | Claude 등 AI 클라이언트가 API 정보를 조회할 수 있는 MCP 프로토콜 지원                                      |
| **로그 뷰어** | 저장된 로그를 조회·검색할 수 있는 REST API 제공 ([viewer](https://github.com/hyuck0221/api-assist-view)) |

---

## 설치

### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.hyuck0221:spring-api-assist:0.0.5")
}
```

### Gradle (Groovy)

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.hyuck0221:spring-api-assist:0.0.5'
}
```

---

## 빠른 시작

`application.yml`에 설정을 추가합니다. 모든 항목은 생략 가능하며 기본값이 적용됩니다.

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

## API 로깅

### 동작 방식

서블릿 필터로 모든 HTTP 요청/응답을 캡처합니다. 캡처된 로그는 활성화된 스토리지 구현체에 저장됩니다. 여러 스토리지를 동시에 활성화할 수 있습니다.

### 로그 항목 (ApiLogEntry)

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | String | ULID 형식 고유 ID |
| `appName` | String? | 설정된 앱 이름 |
| `url` | String | 요청 URL |
| `method` | String | HTTP 메서드 |
| `queryParams` | Map | 쿼리 파라미터 |
| `requestHeaders` | Map | 요청 헤더 (마스킹 적용) |
| `requestBody` | String? | 요청 바디 |
| `responseStatus` | Int | HTTP 상태 코드 |
| `responseContentType` | String? | 응답 Content-Type |
| `responseBody` | String? | 응답 바디 |
| `requestTime` | LocalDateTime | 요청 시각 |
| `responseTime` | LocalDateTime | 응답 시각 |
| `processingTimeMs` | Long | 처리 시간 (ms) |
| `serverName` | String? | 서버 호스트명 |
| `serverPort` | Int? | 서버 포트 |
| `remoteAddr` | String? | 클라이언트 IP |

### 설정

```yaml
api-assist:
  log:
    enabled: true                  # 로깅 활성화 (기본값: true)
    app-name: my-app               # 로그에 기록될 앱 이름 (기본값: "")
    include-paths:                 # 로깅할 경로 패턴 (기본값: 전체)
      - /**
    exclude-paths:                 # 제외할 경로 패턴
      - /swagger-ui/**
      - /actuator/**
    mask-headers:                  # 마스킹할 헤더 (기본값: Authorization, Cookie, Set-Cookie)
      - Authorization
      - Cookie
      - Set-Cookie
    mask-request-body: false       # 요청 바디 마스킹 (기본값: false)
    mask-response-body: false      # 응답 바디 마스킹 (기본값: false)
    mask-request-fields:           # 요청 바디 특정 필드 마스킹 (application/json 에만 적용)
      - password
      - user.creditCard
    mask-response-fields:          # 응답 바디 특정 필드 마스킹 (application/json 에만 적용)
      - accessToken
    max-body-size: 10000           # 최대 바디 캡처 크기(bytes) (기본값: 10000)
```

> 필드명만 지정하면 중첩 객체를 포함한 전체 트리에서 해당 이름의 필드를 마스킹합니다. 특정 경로만 마스킹하려면 점 표기법(`user.creditCard`)을 사용하세요.

> SSE(text/event-stream) 요청은 자동으로 로깅에서 제외됩니다.

---

## 스토리지

### 1. 데이터베이스 (Spring JDBC)

애플리케이션에서 사용하는 DataSource를 공유합니다. `spring-boot-starter-jdbc` 의존성이 필요합니다.

```yaml
api-assist:
  log:
    storage:
      db:
        enabled: true
        table-name: api_logs        # 테이블명 (기본값: api_logs)
        auto-create-table: true     # 자동 테이블 생성 (기본값: true)
```

### 2. 로컬 파일

로컬 파일시스템에 JSON 또는 CSV 형식으로 저장합니다.

```yaml
api-assist:
  log:
    storage:
      local-file:
        enabled: true
        path: ./logs/api            # 저장 경로 (기본값: ./logs/api)
        logs-per-file: 1000         # 파일당 최대 로그 수 (기본값: 1000)
        flush-interval-seconds: 0   # 주기적 플러시 간격 (0 = 버퍼가 찰 때만)
        format: JSON                # JSON 또는 CSV (기본값: JSON)
```

### 3. Supabase DB (PostgreSQL)

별도의 Supabase PostgreSQL 인스턴스에 저장합니다.

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

### 4. Supabase S3 (S3 호환 스토리지)

Supabase Storage (S3 호환)에 파일로 저장합니다.

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
        format: JSON                # JSON 또는 CSV
```

---

## API 문서화

Spring MVC 컨트롤러를 자동으로 스캔하여 API 정보를 제공합니다. Swagger(`@Operation`, `@Tag`) 또는 Swagger 2.x(`@ApiOperation`, `@Api`) 애너테이션을 인식합니다.

### 설정

```yaml
api-assist:
  document:
    enabled: true                          # 활성화 (기본값: true)
    view:
      base-path: /api/documents            # 엔드포인트 기본 경로 (기본값: /api/documents)
```

### 엔드포인트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/api/documents/status` | 활성화 상태 확인 |
| `GET` | `/api/documents/categories` | API 카테고리 목록 |
| `GET` | `/api/documents` | API 목록 조회 (페이징/검색) |

#### `GET /api/documents` 쿼리 파라미터

| 파라미터 | 기본값 | 설명 |
|----------|--------|------|
| `keyword` | - | 키워드 검색 (URL, 제목, 설명) |
| `category` | - | 카테고리 필터 |
| `method` | - | HTTP 메서드 필터 |
| `page` | `0` | 페이지 번호 |
| `size` | `20` | 페이지 크기 |

---

## MCP 서버

Claude 등 MCP 지원 AI 클라이언트가 애플리케이션의 API 정보를 실시간으로 조회할 수 있습니다.

### 설정

```yaml
api-assist:
  mcp:
    enabled: true
    server-info:
      name: My App MCP Server      # MCP 서버 이름
      version: 1.0.0               # MCP 서버 버전
    transport:
      sse:
        enabled: true
        base-path: /mcp/sse        # SSE 엔드포인트 (기본값: /mcp/sse)
      http:
        enabled: true
        base-path: /mcp            # HTTP 엔드포인트 (기본값: /mcp)
```

### 제공 도구 (MCP Tools)

| 도구 | 설명 | 파라미터 |
|------|------|----------|
| `getApiCount` | API 수 조회 | `category` (선택) |
| `getApiDetail` | 특정 API 상세 정보 | `url` (필수), `method` (필수) |
| `searchApis` | API 검색 | `keyword`, `category`, `method` (모두 선택) |

### Claude Desktop 연동 예시

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

## 로그 뷰어 API

저장된 로그를 REST API로 조회합니다. **기본적으로 비활성화**되어 있으며 DB 스토리지가 필요합니다.

### 설정

```yaml
api-assist:
  api-key: your-secret-key         # API 키 인증 (생략 시 인증 없음)
  log:
    view:
      enabled: true
      base-path: /api/logs         # 기본값: /api/logs
```

### 엔드포인트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/api/logs` | 로그 목록 조회 (페이징/필터) |
| `GET` | `/api/logs/{id}` | 특정 로그 조회 |
| `GET` | `/api/logs/stats` | 로그 통계 |
| `GET` | `/api/logs/apps` | 앱 이름 목록 |
| `POST` | `/api/logs/receive` | 외부 로그 수신 |
| `GET` | `/api/logs/files` | 로그 파일 목록 |
| `GET` | `/api/logs/files/content` | 로그 파일 내용 조회 |

#### `GET /api/logs` 쿼리 파라미터

| 파라미터 | 기본값 | 설명 |
|----------|--------|------|
| `appName` | - | 앱 이름 필터 |
| `method` | - | HTTP 메서드 필터 |
| `url` | - | URL 필터 |
| `statusCode` | - | 상태 코드 필터 |
| `startTime` | - | 시작 시각 |
| `endTime` | - | 종료 시각 |
| `minProcessingTimeMs` | - | 최소 처리 시간(ms) |
| `page` | `0` | 페이지 번호 |
| `size` | `20` | 페이지 크기 |
| `sortBy` | `request_time` | 정렬 기준 |
| `sortDir` | `DESC` | 정렬 방향 |

---

## 전체 설정 예시

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
    mask-request-fields:
      - password
      - user.creditCard
    mask-response-fields:
      - accessToken
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

## 요구사항

| 항목 | 버전 |
|------|------|
| Java | 17 이상 |
| Spring Boot | 3.x |
| Kotlin | 선택 사항 (Java도 지원) |
| spring-boot-starter-jdbc | DB 스토리지 사용 시 필요 |
