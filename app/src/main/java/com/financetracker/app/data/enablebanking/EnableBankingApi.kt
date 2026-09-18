package com.financetracker.app.data.enablebanking

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class EnableBankingNotConfiguredException :
    Exception("Add ENABLE_BANKING_APPLICATION_ID and ENABLE_BANKING_PRIVATE_KEY_B64 to local.properties and rebuild.")

class EnableBankingApiException(message: String) : Exception(message)

/**
 * Thin raw-HTTP client for the Enable Banking REST API. Uses HttpURLConnection and org.json
 * (both bundled with the Android platform) instead of adding a networking/JSON dependency —
 * this project has twice broken release builds when a reflection-heavy third-party library
 * lost a class to R8 (see proguard-rules.pro), so platform-only APIs sidestep that risk here.
 */
internal object EnableBankingApi {
    private const val BASE_URL = "https://api.enablebanking.com"

    data class ApiResponse(val status: Int, val body: String)

    fun get(pathAndQuery: String): ApiResponse = request("GET", pathAndQuery, null)

    fun post(path: String, body: JSONObject): ApiResponse = request("POST", path, body)

    private fun request(method: String, pathAndQuery: String, body: JSONObject?): ApiResponse {
        val jwt = EnableBankingJwt.create() ?: throw EnableBankingNotConfiguredException()
        val connection = URL("$BASE_URL$pathAndQuery").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Authorization", "Bearer $jwt")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return ApiResponse(status, text)
        } finally {
            connection.disconnect()
        }
    }
}
