package com.financetracker.app

import com.financetracker.app.data.ai.AiProvider
import com.financetracker.app.data.ai.AiProvider.CLAUDE
import com.financetracker.app.data.ai.AiProvider.GEMINI
import com.financetracker.app.data.ai.AiProvider.GROQ
import com.financetracker.app.data.ai.aiChain
import org.junit.Assert.assertEquals
import org.junit.Test

class AiChainTest {

    private val all: (AiProvider) -> Boolean = { true }

    @Test
    fun `the chosen AI goes first, the rest keep their default order`() {
        assertEquals(listOf(GEMINI, GROQ, CLAUDE), aiChain(GEMINI, all))
        assertEquals(listOf(GROQ, GEMINI, CLAUDE), aiChain(GROQ, all))
        assertEquals(listOf(CLAUDE, GEMINI, GROQ), aiChain(CLAUDE, all))
    }

    @Test
    fun `AIs without a key are skipped, including the chosen one`() {
        assertEquals(listOf(GEMINI, GROQ), aiChain(CLAUDE) { it != CLAUDE })
        assertEquals(listOf(GROQ), aiChain(GEMINI) { it == GROQ })
        assertEquals(emptyList<AiProvider>(), aiChain(GEMINI) { false })
    }
}
