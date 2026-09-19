package com.financetracker.app.data.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolUseBlock
import com.financetracker.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MODEL_ID = "claude-opus-5"
private const val PROPOSE_BUDGET_TOOL_NAME = "propose_budget"

data class ChatTurn(val isUser: Boolean, val text: String)

data class BudgetCategoryProposal(val mainCategory: String, val category: String, val amount: Double)

/** [accountName] is the exact account name to scope the budget to, or null/"All accounts" for a
 * combined budget across every account — resolved back to a real accountId by the caller. */
data class BudgetProposal(
    val accountName: String?,
    val overallAmount: Double?,
    val categoryBudgets: List<BudgetCategoryProposal>,
    val summary: String?
)

data class AiChatResult(val text: String, val proposal: BudgetProposal?)

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

    /**
     * Same as [chat], but Claude may also call a "propose_budget" tool instead of (or alongside)
     * replying in plain text — used for "recommend and set up a budget for me" requests. The
     * tool call is never executed automatically: it's parsed into a [BudgetProposal] for the
     * caller to show the user and apply only on confirmation.
     */
    suspend fun chatWithBudgetTool(
        cachedContext: String,
        history: List<ChatTurn>,
        userMessage: String,
        maxTokens: Long = 2048L
    ): Result<AiChatResult> {
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
                    .addTool(proposeBudgetTool())
                history.forEach { turn ->
                    if (turn.isUser) builder.addUserMessage(turn.text) else builder.addAssistantMessage(turn.text)
                }
                builder.addUserMessage(userMessage)
                Result.success(extractChatResult(anthropic.messages().create(builder.build())))
            } catch (e: Exception) {
                Result.failure(mapError(e))
            }
        }
    }

    private fun proposeBudgetTool(): Tool {
        val properties = Tool.InputSchema.Properties.builder()
            .putAdditionalProperty(
                "account_name",
                JsonValue.from(
                    mapOf(
                        "type" to "string",
                        "description" to "Exact account name from the ACCOUNTS list this budget " +
                            "applies to, or \"All accounts\" for a combined budget across every account."
                    )
                )
            )
            .putAdditionalProperty(
                "overall_amount",
                JsonValue.from(
                    mapOf(
                        "type" to "number",
                        "description" to "Suggested overall monthly spending limit across all " +
                            "expense categories combined. Omit if not proposing one."
                    )
                )
            )
            .putAdditionalProperty(
                "category_budgets",
                JsonValue.from(
                    mapOf(
                        "type" to "array",
                        "description" to "Suggested monthly limits for specific categories.",
                        "items" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "main_category" to mapOf("type" to "string"),
                                "category" to mapOf("type" to "string"),
                                "amount" to mapOf("type" to "number")
                            ),
                            "required" to listOf("main_category", "category", "amount")
                        )
                    )
                )
            )
            .putAdditionalProperty(
                "summary",
                JsonValue.from(
                    mapOf(
                        "type" to "string",
                        "description" to "One or two sentence explanation of the reasoning " +
                            "behind this budget proposal, to show the user."
                    )
                )
            )
            .build()

        val inputSchema = Tool.InputSchema.builder()
            .type(JsonValue.from("object"))
            .properties(properties)
            .build()

        return Tool.builder()
            .name(PROPOSE_BUDGET_TOOL_NAME)
            .description(
                "Propose a monthly budget based on the user's transaction history, for " +
                    "whatever time range or categories they ask about — not limited to any " +
                    "fixed period. This only shows a proposal for the user to review; it does " +
                    "NOT apply the budget automatically."
            )
            .inputSchema(inputSchema)
            .build()
    }

    private fun extractChatResult(message: Message): AiChatResult {
        val textBuilder = StringBuilder()
        var proposal: BudgetProposal? = null
        for (block in message.content()) {
            if (block.isText()) {
                textBuilder.append(block.asText().text())
            } else if (block.isToolUse()) {
                val toolUse = block.asToolUse()
                if (toolUse.name() == PROPOSE_BUDGET_TOOL_NAME) {
                    proposal = parseBudgetProposal(toolUse) ?: proposal
                }
            }
        }
        return AiChatResult(textBuilder.toString().trim(), proposal)
    }

    private fun parseBudgetProposal(toolUse: ToolUseBlock): BudgetProposal? = try {
        @Suppress("UNCHECKED_CAST")
        val input = toolUse._input().convert(Map::class.java) as? Map<String, Any?>
        if (input == null) {
            null
        } else {
            val overallAmount = (input["overall_amount"] as? Number)?.toDouble()
            val categoryBudgets = (input["category_budgets"] as? List<*>).orEmpty().mapNotNull { entry ->
                val fields = entry as? Map<*, *> ?: return@mapNotNull null
                val main = fields["main_category"] as? String ?: return@mapNotNull null
                val category = fields["category"] as? String ?: return@mapNotNull null
                val amount = (fields["amount"] as? Number)?.toDouble() ?: return@mapNotNull null
                BudgetCategoryProposal(main, category, amount)
            }
            if (overallAmount == null && categoryBudgets.isEmpty()) {
                null
            } else {
                BudgetProposal(
                    accountName = input["account_name"] as? String,
                    overallAmount = overallAmount,
                    categoryBudgets = categoryBudgets,
                    summary = input["summary"] as? String
                )
            }
        }
    } catch (e: Exception) {
        null
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
