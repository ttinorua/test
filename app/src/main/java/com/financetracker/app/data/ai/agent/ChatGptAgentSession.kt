package com.financetracker.app.data.ai.agent

import com.financetracker.app.data.ai.ChatTurn
import com.financetracker.app.data.ai.chatgpt.ChatGptService
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ask AI on the person's ChatGPT plan. Nothing is stored at OpenAI on this route, so every step
 * sends the whole exchange again: the earlier output items (with their encrypted thinking) followed
 * by the function results. The plan route wants function tools grouped in a namespace.
 */
internal class ChatGptAgentSession(
    system: String,
    history: List<ChatTurn>,
    userMessage: String,
    tools: List<ToolSpec>
) : AgentSession {

    private val body: JSONObject = ChatGptService.request(
        system, history + ChatTurn(isUser = true, text = userMessage), quick = false
    ).put("tools", toolsJson(tools))

    override fun step(): AgentStep {
        val response = ChatGptService.send(body)
        val input = body.getJSONArray("input")
        val keepThinking = body.has("include")
        val calls = mutableListOf<ToolCall>()
        val output = response.optJSONArray("output") ?: JSONArray()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            when (item.optString("type")) {
                "reasoning" -> if (keepThinking) input.put(item)
                "function_call" -> {
                    val args = runCatching { JSONObject(item.optString("arguments").ifBlank { "{}" }) }.getOrElse { JSONObject() }
                    calls += ToolCall(item.optString("call_id"), item.optString("name"), args)
                    input.put(echo(item))
                }
                "message" -> input.put(echo(item))
            }
        }
        return AgentStep(ChatGptService.textOf(response), calls)
    }

    override fun addToolResults(results: List<Pair<ToolCall, String>>) {
        val input = body.getJSONArray("input")
        results.forEach { (call, result) ->
            input.put(JSONObject().put("type", "function_call_output").put("call_id", call.id).put("output", result))
        }
    }

    companion object {
        private const val NAMESPACE = "finance_app"

        internal fun toolsJson(tools: List<ToolSpec>): JSONArray {
            val functions = JSONArray()
            tools.forEach {
                functions.put(
                    JSONObject().put("type", "function").put("name", it.name)
                        .put("description", it.description).put("parameters", it.parameters)
                )
            }
            return JSONArray().put(
                JSONObject().put("type", "namespace").put("name", NAMESPACE)
                    .put("description", "The Finance Tracker app's own functions for the person's money data.")
                    .put("tools", functions)
            )
        }

        /** An output item sent back as input: without its server id, which isn't kept on this route. */
        private fun echo(item: JSONObject): JSONObject = JSONObject(item.toString()).apply {
            remove("id")
            remove("status")
        }
    }
}
