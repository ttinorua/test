package com.financetracker.app.data.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolUseBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MODEL_ID = "claude-opus-5-5"

/** Claude Opus 5.5 always thinks before answering, and that thinking counts toward max_tokens —
 * each caller's limit is sized for the reply alone, so this much is added on top. */
private const val THINKING_HEADROOM_TOKENS = 8_000L

/** Server-side refusal fallback: if a safety classifier declines a request (rare for a finance
 * app, but a false positive would otherwise just fail), the API retries it on the model
 * Anthropic recommends for that case, inside the same call. */
private const val FALLBACK_BETA_HEADER = "server-side-fallback-2026-07-01"
private const val PRESENT_INSIGHTS_TOOL_NAME = "present_insights"

data class ChatTurn(val isUser: Boolean, val text: String)

enum class InsightTone { POSITIVE, NEUTRAL, WARNING }

/** One dashboard insight card. [value] is the headline figure, already formatted (with currency
 * unit or "%" as appropriate) since Claude has the real numbers and formatting conventions in
 * its context — the UI just displays it verbatim. */
data class InsightCard(val label: String, val value: String, val detail: String, val tone: InsightTone)

class AiNotConfiguredException :
    Exception("No AI key is set up. Add one in Settings > General > AI assistant.")

class AiRequestException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Thin wrapper around the Anthropic Java SDK, used when Claude is the chosen provider (see
 * [AiService]). The API key is the user's own from Settings, or else one built into the app
 * (see [AiSettings]) — there's no backend server in between.
 */
object ClaudeService {

    @Volatile
    private var cachedClient: Pair<String, AnthropicClient>? = null

    /** A client for the current Claude key, rebuilt only when the key changes. */
    internal fun client(): AnthropicClient? {
        val key = AiSettings.keyFor(AiProvider.CLAUDE) ?: return null
        cachedClient?.takeIf { it.first == key }?.let { return it.second }
        return AnthropicOkHttpClient.builder().apiKey(key).build().also { cachedClient = key to it }
    }

    /** One-shot request: a plain system prompt plus a single user message. [maxTokens] is the
     * budget for the reply itself; thinking headroom is added on top. */
    suspend fun ask(
        systemPrompt: String,
        userMessage: String,
        maxTokens: Long = 1024L,
        effort: OutputConfig.Effort = OutputConfig.Effort.MEDIUM
    ): Result<String> {
        val anthropic = client() ?: return Result.failure(AiNotConfiguredException())
        return withContext(Dispatchers.IO) {
            try {
                val params = baseParams(maxTokens, effort)
                    .system(systemPrompt)
                    .addUserMessage(userMessage)
                    .build()
                Result.success(extractText(send(anthropic, params)))
            } catch (e: Exception) {
                Result.failure(mapError(e))
            }
        }
    }

    /**
     * One-shot request asking Claude to answer via the "present_insights" tool instead of prose,
     * so the dashboard can render each observation as its own stat card rather than a wall of
     * text. Claude Opus 5.5 rejects a forced tool choice, so the tool is requested in the prompt
     * and the call retried once if Claude answered in prose instead. Returns an empty list (never
     * a failure) if no usable tool call came back — the caller decides how to surface that.
     */
    suspend fun generateInsights(systemPrompt: String, userMessage: String, maxTokens: Long = 1024L): Result<List<InsightCard>> {
        val anthropic = client() ?: return Result.failure(AiNotConfiguredException())
        return withContext(Dispatchers.IO) {
            try {
                val params = baseParams(maxTokens, OutputConfig.Effort.MEDIUM)
                    .system("$systemPrompt\n\nRespond only by calling the $PRESENT_INSIGHTS_TOOL_NAME tool.")
                    .addUserMessage(userMessage)
                    .addTool(presentInsightsTool())
                    .build()
                val cards = extractInsightCards(send(anthropic, params))
                    .ifEmpty { extractInsightCards(send(anthropic, params)) }
                Result.success(cards)
            } catch (e: Exception) {
                Result.failure(mapError(e))
            }
        }
    }

    /** Model, token budget (reply + thinking headroom), effort, and the refusal fallback —
     * shared by every request. */
    internal fun baseParams(replyTokens: Long, effort: OutputConfig.Effort): MessageCreateParams.Builder =
        MessageCreateParams.builder()
            .model(MODEL_ID)
            .maxTokens(replyTokens + THINKING_HEADROOM_TOKENS)
            .outputConfig(OutputConfig.builder().effort(effort).build())
            .putAdditionalHeader("anthropic-beta", FALLBACK_BETA_HEADER)
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))

    /** A safety-classifier decline still comes back as HTTP 200, so check for it before reading
     * the content — otherwise it would look like an empty answer. */
    internal fun send(anthropic: AnthropicClient, params: MessageCreateParams): Message {
        val message = anthropic.messages().create(params)
        if (message.stopReason().orElse(null) == StopReason.REFUSAL) {
            throw AiRequestException("Claude declined this request. Try rephrasing it.")
        }
        return message
    }

    private fun presentInsightsTool(): Tool {
        val properties = Tool.InputSchema.Properties.builder()
            .putAdditionalProperty(
                "insights",
                JsonValue.from(
                    mapOf(
                        "type" to "array",
                        "description" to "2 to 4 short, concrete insights, most important first.",
                        "items" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "label" to mapOf(
                                    "type" to "string",
                                    "description" to "Short headline, 2-4 words, e.g. \"Media spending\"."
                                ),
                                "value" to mapOf(
                                    "type" to "string",
                                    "description" to "The headline figure, already formatted with " +
                                        "the display currency or unit, e.g. \"3,319.99 kr\" or \"37% of outflows\"."
                                ),
                                "detail" to mapOf(
                                    "type" to "string",
                                    "description" to "One short sentence of concrete context, e.g. " +
                                        "what it's made up of or compared to."
                                ),
                                "tone" to mapOf(
                                    "type" to "string",
                                    "enum" to listOf("positive", "neutral", "warning"),
                                    "description" to "\"positive\" for good news (money left over, " +
                                        "spending down), \"warning\" for something worth the user's " +
                                        "attention (overspending, an unusually large outflow), " +
                                        "otherwise \"neutral\"."
                                )
                            ),
                            "required" to listOf("label", "value", "detail", "tone")
                        )
                    )
                )
            )
            .build()

        val inputSchema = Tool.InputSchema.builder()
            .type(JsonValue.from("object"))
            .properties(properties)
            .build()

        return Tool.builder()
            .name(PRESENT_INSIGHTS_TOOL_NAME)
            .description(
                "Present 2-4 concise, concrete insights about the user's spending as structured " +
                    "stat cards for a quick-glance dashboard widget, instead of a paragraph of prose."
            )
            .inputSchema(inputSchema)
            .build()
    }

    private fun extractInsightCards(message: Message): List<InsightCard> {
        for (block in message.content()) {
            if (block.isToolUse()) {
                val toolUse = block.asToolUse()
                if (toolUse.name() == PRESENT_INSIGHTS_TOOL_NAME) {
                    return parseInsightCards(toolUse)
                }
            }
        }
        return emptyList()
    }

    private fun parseInsightCards(toolUse: ToolUseBlock): List<InsightCard> = try {
        @Suppress("UNCHECKED_CAST")
        val input = toolUse._input().convert(Map::class.java) as? Map<String, Any?>
        (input?.get("insights") as? List<*>).orEmpty().mapNotNull { entry ->
            val fields = entry as? Map<*, *> ?: return@mapNotNull null
            val label = fields["label"] as? String ?: return@mapNotNull null
            val value = fields["value"] as? String ?: return@mapNotNull null
            val detail = fields["detail"] as? String ?: return@mapNotNull null
            val tone = when ((fields["tone"] as? String)?.lowercase()) {
                "positive" -> InsightTone.POSITIVE
                "warning" -> InsightTone.WARNING
                else -> InsightTone.NEUTRAL
            }
            InsightCard(label, value, detail, tone)
        }
    } catch (e: Exception) {
        emptyList()
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

    internal fun mapError(t: Throwable): Throwable = when (t) {
        is AiRequestException -> t
        is AnthropicServiceException -> AiRequestException(
            "Claude request failed (${t.statusCode()}): " +
                t.errorType().map { it.toString() }.orElse(t.message ?: "unknown error"),
            t
        )
        else -> AiRequestException("Claude request failed: ${t.message}", t)
    }
}
