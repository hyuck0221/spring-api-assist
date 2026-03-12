package com.hshim.springapiassist.log.filter

import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

internal class CachingRequestWrapper(request: HttpServletRequest) : HttpServletRequestWrapper(request) {

    val cachedBody: ByteArray = request.inputStream.readBytes()

    override fun getInputStream(): ServletInputStream = object : ServletInputStream() {
        private val buf = ByteArrayInputStream(cachedBody)
        override fun read(): Int = buf.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = buf.read(b, off, len)
        override fun isFinished(): Boolean = buf.available() == 0
        override fun isReady(): Boolean = true
        override fun setReadListener(listener: ReadListener?) {}
    }

    override fun getReader(): BufferedReader =
        BufferedReader(InputStreamReader(ByteArrayInputStream(cachedBody), characterEncoding ?: "UTF-8"))
}
