package com.financetracker.app.ui.screens.ai

/** A question to ask the AI advisor as soon as its screen opens (e.g. Ask advisor on a
 * "Needs your attention" card). */
object AdvisorLaunch {
    @Volatile
    private var pending: String? = null

    fun ask(prompt: String) {
        pending = prompt
    }

    fun take(): String? = pending.also { pending = null }
}
