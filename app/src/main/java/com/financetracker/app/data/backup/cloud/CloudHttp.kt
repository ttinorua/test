package com.financetracker.app.data.backup.cloud

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class CloudException(message: String, val status: Int = 0) : Exception(message)

/** Minimal HTTP for the cloud backup destinations — platform HttpURLConnection, like the Enable
 * Banking client, rather than another networking library. */
internal object CloudHttp {

    data class Response(val status: Int, val body: String) {
        val ok: Boolean get() = status in 200..299
    }

    fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        contentType: String? = null
    ): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (body != null) {
                connection.doOutput = true
                contentType?.let { connection.setRequestProperty("Content-Type", it) }
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return Response(status, text)
        } finally {
            connection.disconnect()
        }
    }

    fun form(vararg fields: Pair<String, String>): ByteArray =
        fields.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }.toByteArray(Charsets.UTF_8)

    fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
