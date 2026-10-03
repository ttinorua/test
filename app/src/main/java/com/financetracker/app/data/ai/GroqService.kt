package com.financetracker.app.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val GROQ_MODEL = "openai/gpt-oss-120b"
private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"

/** GPT-OSS reasons before answering and that counts toward max_completion_tokens, so each caller's
 * reply budget gets this much on top. */
private const val GROQ_REASONING_HEADROOM = 2_000

/**
 * Groq over its OpenAI-compatible chat API (platform HttpURLConnection + org.json, no SDK),
 * offering the same operations as [GeminiService] and [ClaudeService].
 */
internal object GroqService {

    suspend fun ask(key: String, systemPrompt: String, userMessage: String, maxTokens: Int, quick: Boolean): String =
        withContext(Dispatchers.IO) {
            val body = request(systemPrompt, listOf(ChatTurn(isUser = true, text = userMessage)), maxTokens, quick)
            messageOf(send(key, body)).optString("content").trim()
        }

    suspend fun generateInsights(key: String, systemPrompt: String, userMessage: String, maxTokens: Int): List<InsightCard> =
        withContext(Dispatchers.IO) {
            val system = systemPrompt + "\n\n" + AiJson.INSIGHTS_INSTRUCTIONS
            val body = request(system, listOf(ChatTurn(isUser = true, text = userMessage)), maxTokens, quick = false)
                .put("response_format", JSONObject().put("type", "json_object"))
            AiJson.parseInsights(messageOf(send(key, body)).optString("content"))
        }

    fun request(system: String, turns: List<ChatTurn>, maxTokens: Int, quick: Boolean): JSONObject {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        turns.forEach { turn ->
            messages.put(JSONObject().put("role", if (turn.isUser) "user" else "assistant").put("content", turn.text))
        }
        return JSONObject()
            .put("model", GROQ_MODEL)
            .put("messages", messages)
            .put("max_completion_tokens", maxTokens + GROQ_REASONING_HEADROOM)
            .put("reasoning_effort", if (quick) "low" else "medium")
    }

    /** Sends [body]; if Groq rejects an optional setting (400), retries once without it. */
    fun send(key: String, body: JSONObject): JSONObject {
        var first = post(key, body)
        // A per-minute limit (several advisor steps in a row can hit it): wait it out once if short.
        if (first.first == 429) {
            val wait = Regex("try again in ([0-9.]+)s").find(first.second)?.groupValues?.get(1)?.toDoubleOrNull()
            if (wait != null && wait <= 20.0) {
                Thread.sleep((wait * 1000).toLong() + 500)
                first = post(key, body)
            }
        }
        val (status, text) = if (first.first == 400) {
            body.remove("reasoning_effort")
            post(key, body)
        } else {
            first
        }
        val json = runCatching { JSONObject(text) }.getOrNull()
        if (status !in 200..299) {
            val message = json?.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() } ?: text.take(200)
            throw AiRequestException(
                when (status) {
                    429 -> "Groq's free limit is used up for now (${message.take(160)})."
                    413 -> "That request is too large for Groq's free tier."
                    else -> "Groq request failed ($status): $message"
                }
            )
        }
        return json ?: throw AiRequestException("Groq sent an unreadable reply.")
    }

    private fun post(key: String, body: JSONObject): Pair<Int, String> {
        val connection = URL(GROQ_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 90_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            return status to (stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "")
        } finally {
            connection.disconnect()
        }
    }

    fun messageOf(response: JSONObject): JSONObject =
        response.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            ?: throw AiRequestException("Groq sent an empty reply.")
}
