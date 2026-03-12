package com.hshim.springapiassist.log.filter

import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter

internal class CachingResponseWrapper(response: HttpServletResponse) : HttpServletResponseWrapper(response) {

    private val buffer = ByteArrayOutputStream()
    private var cachedOutputStream: ServletOutputStream? = null
    private var cachedWriter: PrintWriter? = null
    private var capturedStatus: Int = SC_OK

    val cachedBody: ByteArray
        get() {
            cachedWriter?.flush()
            cachedOutputStream?.flush()
            return buffer.toByteArray()
        }

    override fun getOutputStream(): ServletOutputStream {
        if (cachedOutputStream == null) {
            cachedOutputStream = object : ServletOutputStream() {
                override fun write(b: Int) = buffer.write(b)
                override fun write(b: ByteArray) = buffer.write(b)
                override fun write(b: ByteArray, off: Int, len: Int) = buffer.write(b, off, len)
                override fun isReady(): Boolean = true
                override fun setWriteListener(listener: WriteListener?) {}
            }
        }
        return cachedOutputStream!!
    }

    override fun getWriter(): PrintWriter {
        if (cachedWriter == null) {
            cachedWriter = PrintWriter(
                OutputStreamWriter(buffer, characterEncoding ?: "UTF-8"),
                true,
            )
        }
        return cachedWriter!!
    }

    override fun getStatus(): Int = capturedStatus

    override fun setStatus(sc: Int) {
        capturedStatus = sc
        super.setStatus(sc)
    }

    override fun sendError(sc: Int) {
        capturedStatus = sc
        super.sendError(sc)
    }

    override fun sendError(sc: Int, msg: String) {
        capturedStatus = sc
        super.sendError(sc, msg)
    }

    fun copyBodyToResponse() {
        cachedWriter?.flush()
        cachedOutputStream?.flush()
        if (buffer.size() > 0) {
            response.outputStream.write(buffer.toByteArray())
            response.outputStream.flush()
        }
    }
}
