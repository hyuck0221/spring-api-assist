package com.hshim.springapiassist.log.storage.supabase

import com.hshim.springapiassist.log.storage.ApiLogStorage
import com.hshim.springapiassist.configuration.properties.ApiLogProperties
import com.hshim.springapiassist.log.enums.LogFileFormat
import com.hshim.springapiassist.log.model.ApiLogEntry
import com.hshim.springapiassist.log.storage.file.ApiLogFileWriter
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.BufferedWriter
import java.io.StringWriter
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ApiLogSupabaseS3Storage(private val properties: ApiLogProperties) : ApiLogStorage {

    private val log = LoggerFactory.getLogger(ApiLogSupabaseS3Storage::class.java)
    private val s3Props = properties.storage.supabaseS3

    private val buffer = mutableListOf<ApiLogEntry>()
    private val lock = ReentrantLock()
    private val fileCounter = AtomicInteger(0)
    private lateinit var s3Client: S3Client

    @PostConstruct
    fun init() {
        require(s3Props.endpointUrl.isNotBlank()) { "apilog.storage.supabase-s3.endpoint-url must not be blank" }
        require(s3Props.accessKeyId.isNotBlank()) { "apilog.storage.supabase-s3.access-key-id must not be blank" }
        require(s3Props.secretAccessKey.isNotBlank()) { "apilog.storage.supabase-s3.secret-access-key must not be blank" }
        require(s3Props.bucket.isNotBlank()) { "apilog.storage.supabase-s3.bucket must not be blank" }

        s3Client = S3Client.builder()
            .endpointOverride(URI.create(s3Props.endpointUrl))
            .region(Region.of(s3Props.region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(s3Props.accessKeyId, s3Props.secretAccessKey),
                ),
            )
            .forcePathStyle(true)
            .build()

        log.info("ApiLog Supabase S3 storage initialized. Bucket: ${s3Props.bucket}")
    }

    override fun save(entry: ApiLogEntry) {
        lock.withLock {
            buffer.add(entry)
            if (buffer.size >= s3Props.logsPerFile) {
                flushToS3(buffer.toList())
                buffer.clear()
            }
        }
    }

    @PreDestroy
    fun shutdown() {
        lock.withLock {
            if (buffer.isNotEmpty()) {
                flushToS3(buffer.toList())
                buffer.clear()
            }
        }
        if (::s3Client.isInitialized) {
            s3Client.close()
        }
    }

    private fun flushToS3(entries: List<ApiLogEntry>) {
        if (entries.isEmpty()) return

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"))
        val counter = fileCounter.getAndIncrement()
        val ext = if (s3Props.format == LogFileFormat.CSV) "csv" else "jsonl"
        val key = "${s3Props.keyPrefix}api_log_${timestamp}_$counter.$ext"

        val contentType = if (s3Props.format == LogFileFormat.CSV) {
            "text/csv; charset=UTF-8"
        } else {
            "application/x-ndjson; charset=UTF-8"
        }

        try {
            val sw = StringWriter()
            BufferedWriter(sw).use { writer ->
                ApiLogFileWriter.writeEntries(
                    writer = writer,
                    entries = entries,
                    format = s3Props.format,
                    writeHeader = true,
                )
            }
            val content = sw.toString()

            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(s3Props.bucket)
                    .key(key)
                    .contentType(contentType)
                    .build(),
                RequestBody.fromString(content),
            )

            log.debug("Uploaded {} log entries to s3://{}/{}", entries.size, s3Props.bucket, key)
        } catch (e: Exception) {
            log.error("Failed to upload log entries to Supabase S3. Key: $key", e)
        }
    }
}
