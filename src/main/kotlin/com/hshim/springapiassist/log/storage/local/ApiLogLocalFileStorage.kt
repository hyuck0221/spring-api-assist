package com.hshim.springapiassist.log.storage.local

import com.hshim.springapiassist.log.storage.ApiLogStorage
import com.hshim.springapiassist.configuration.properties.ApiLogProperties
import com.hshim.springapiassist.log.enums.LogFileFormat
import com.hshim.springapiassist.log.model.ApiLogEntry
import com.hshim.springapiassist.log.storage.file.ApiLogFileWriter
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ApiLogLocalFileStorage(private val properties: ApiLogProperties) : ApiLogStorage {

    private val log = LoggerFactory.getLogger(ApiLogLocalFileStorage::class.java)
    private val localProps = properties.storage.localFile

    private val buffer = mutableListOf<ApiLogEntry>()
    private val lock = ReentrantLock()
    private val fileCounter = AtomicInteger(0)
    private var scheduler: ScheduledExecutorService? = null

    @PostConstruct
    fun init() {
        val dir = File(localProps.path)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        log.info("ApiLog local file storage initialized. Directory: ${dir.absolutePath}")

        val interval = localProps.flushIntervalSeconds
        if (interval > 0) {
            scheduler = Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "apilog-file-flush").apply { isDaemon = true }
            }
            scheduler!!.scheduleAtFixedRate(
                {
                    lock.withLock {
                        if (buffer.isNotEmpty()) {
                            flushToFile(buffer.toList())
                            buffer.clear()
                        }
                    }
                },
                interval,
                interval,
                TimeUnit.SECONDS,
            )
            log.info("ApiLog local file storage will flush every ${interval}s.")
        }
    }

    override fun save(entry: ApiLogEntry) {
        lock.withLock {
            buffer.add(entry)
            if (buffer.size >= localProps.logsPerFile) {
                flushToFile(buffer.toList())
                buffer.clear()
            }
        }
    }

    @PreDestroy
    fun shutdown() {
        scheduler?.shutdownNow()
        lock.withLock {
            if (buffer.isNotEmpty()) {
                flushToFile(buffer.toList())
                buffer.clear()
            }
        }
    }

    private fun flushToFile(entries: List<ApiLogEntry>) {
        if (entries.isEmpty()) return

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"))
        val counter = fileCounter.getAndIncrement()
        val ext = if (localProps.format == LogFileFormat.CSV) "csv" else "jsonl"
        val file = File(localProps.path, "api_log_${timestamp}_$counter.$ext")

        try {
            file.bufferedWriter(Charsets.UTF_8).use { writer ->
                ApiLogFileWriter.writeEntries(
                    writer = writer,
                    entries = entries,
                    format = localProps.format,
                    writeHeader = true,
                )
            }
            log.debug("Wrote {} log entries to {}", entries.size, file.absolutePath)
        } catch (e: Exception) {
            log.error("Failed to write log entries to file: ${file.absolutePath}", e)
        }
    }
}
