package com.financetracker.app.data.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.TextBlockParam
import com.financetracker.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MODEL_ID = "claude-opus-5"

data class ChatTurn(val isUser: Boolean, val text: String)

class AiNotConfiguredException :
    Exception("Add your Anthropic API key to local.properties (ANTHROPIC_API_KEY=...) and rebuild.")

class AiRequestException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Thin wrapper around the Anthropic Java SDK. The API key ships inside this app's
 * BuildConfig (set via local.properties, never committed) rather than a backend
 * server — a deliberate tradeoff acceptable only because this app is shared
 * privately with family, never published.
 */
object ClaudeService {

    val isConfigured: Boolean get() = BuildConfig.ANTHROPIC_API_KEY.isNotBlank()

    private val client: AnthropicClient? by lazy {
        if (isConfigured) {
            AnthropicOkHttpClient.builder().apiKey(BuildConfig.ANTHROPIC_API_KEY).build()
        } else {
            null
        }
    }

    /** One-shot request: a plain system prompt plus a single user message. */
    suspend fun ask(systemPrompt: String, userMessage: String, maxTokens: Long = 1024L): Result<String> {
        val anthropic = client ?: return Result.failure(AiNotConfiguredException())
        return withContext(Dispatchers.IO) {
            try {
                val params = MessageCreateParams.builder()
                    .model(MODEL_ID)
                    .maxTokens(maxTokens)
                    .system(systemPrompt)
                    .addUserMessage(userMessage)
                    .build()
                Result.success(extractText(anthropic.messages().create(params)))
            } catch (e: Exception) {
                Result.failure(mapError(e))
            }
        }
    }

    /**
     * Multi-turn chat. [cachedContext] (a dump of the user's transactions) is sent as a
     * cached system block so repeated turns in the same session don't re-bill its full
     * token cost. [history] is the prior conversation, oldest first, NOT including the
     * new [userMessage].
     */
    suspend fun chat(
        cachedContext: String,
        history: List<ChatTurn>,
        userMessage: String,
        maxTokens: Long = 2048L
    ): Result<String> {
        val anthropic = client ?: return Result.failure(AiNotConfiguredException())
        return withContext(Dispatchers.IO) {
            try {
                val builder = MessageCreateParams.builder()
                    .model(MODEL_ID)
                    .maxTokens(maxTokens)
                    .systemOfTextBlockParams(
                        listOf(
                            TextBlockParam.builder()
                                .text(cachedContext)
                                .cacheControl(CacheControlEphemeral.builder().build())
                                .build()
                        )
                    )
                history.forEach { turn ->
                    if (turn.isUser) builder.addUserMessage(turn.text) else builder.addAssistantMessage(turn.text)
                }
                builder.addUserMessage(userMessage)
                Result.success(extractText(anthropic.messages().create(builder.build())))
            } catch (e: Exception) {
                Result.failure(mapError(e))
            }
        }
    }

    private fun extractText(message: Message): String {
        val builder = StringBuilder()
        for (block in message.content()) {
            if (block.isText()) {
                builder.append(block.asText().text())
            }
        }
        return builder.toString().trim()
    }

    private fun mapError(t: Throwable): Throwable = when (t) {
        is AnthropicServiceException -> AiRequestException(
            "Claude request failed (${t.statusCode()}): " +
                t.errorType().map { it.toString() }.orElse(t.message ?: "unknown error"),
            t
        )
        else -> AiRequestException("Claude request failed: ${t.message}", t)
    }
}
