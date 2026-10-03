package com.financetracker.app.data.ai

import com.anthropic.models.messages.OutputConfig

/**
 * The one entry point the rest of the app uses for AI, sending each request to whichever
 * provider is chosen in [AiSettings] — Gemini ([GeminiService]) or Claude ([ClaudeService]).
 */
object AiService {

    val provider: AiProvider get() = AiSettings.provider

    val isConfigured: Boolean get() = AiSettings.keyFor(provider) != null

    /** One-shot question. [quick] is for simple lookups (e.g. picking a category), answered with
     * less thinking so they're faster. */
    suspend fun ask(systemPrompt: String, userMessage: String, maxTokens: Long = 1024L, quick: Boolean = false): Result<String> =
        when (provider) {
            AiProvider.CLAUDE -> ClaudeService.ask(
                systemPrompt,
                userMessage,
                maxTokens,
                if (quick) OutputConfig.Effort.LOW else OutputConfig.Effort.MEDIUM
            )
            AiProvider.GEMINI -> gemini { key -> GeminiService.ask(key, systemPrompt, userMessage, maxTokens.toInt(), quick) }
        }

    suspend fun generateInsights(systemPrompt: String, userMessage: String, maxTokens: Long = 1024L): Result<List<InsightCard>> =
        when (provider) {
            AiProvider.CLAUDE -> ClaudeService.generateInsights(systemPrompt, userMessage, maxTokens)
            AiProvider.GEMINI -> gemini { key -> GeminiService.generateInsights(key, systemPrompt, userMessage, maxTokens.toInt()) }
        }

    suspend fun chatWithBudgetTool(
        cachedContext: String,
        history: List<ChatTurn>,
        userMessage: String,
        maxTokens: Long = 2048L
    ): Result<AiChatResult> = when (provider) {
        AiProvider.CLAUDE -> ClaudeService.chatWithBudgetTool(cachedContext, history, userMessage, maxTokens)
        AiProvider.GEMINI -> gemini { key ->
            GeminiService.chatWithBudgetTool(key, cachedContext, history, userMessage, maxTokens.toInt())
        }
    }

    private inline fun <T> gemini(block: (String) -> T): Result<T> {
        val key = AiSettings.keyFor(AiProvider.GEMINI) ?: return Result.failure(AiNotConfiguredException())
        return try {
            Result.success(block(key))
        } catch (e: AiRequestException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(AiRequestException("Gemini request failed: ${e.message}", e))
        }
    }
}
