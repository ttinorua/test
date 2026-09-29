package com.financetracker.app

import com.financetracker.app.data.ai.LearnedCategoryRules
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [LearnedCategoryRules] is a singleton, so — since [LearnedCategoryRules.init] (which would
 * enable persistence) is never called here — every test below only exercises its in-memory
 * behavior, matching how it behaves before Android's SharedPreferences are available. Each test
 * uses its own distinct note text so state accumulated by earlier tests in this run never
 * interferes with another test's assertions. */
class LearnedCategoryRulesTest {

    private fun category(id: Long, name: String) = Category(
        id = id,
        name = name,
        mainCategory = "Media",
        type = TransactionType.EXPENSE
    )

    @Test
    fun `a learned note returns its category`() {
        val media = category(1L, "Streaming")
        LearnedCategoryRules.learn("Netflix Inc", media.id)

        assertEquals(media, LearnedCategoryRules.suggest("Netflix Inc", listOf(media)))
    }

    @Test
    fun `matching is case-insensitive and trims whitespace`() {
        val media = category(2L, "Streaming")
        LearnedCategoryRules.learn("Spotify AB", media.id)

        assertEquals(media, LearnedCategoryRules.suggest("  SPOTIFY AB  ", listOf(media)))
    }

    @Test
    fun `a note that was never learned returns null`() {
        val media = category(3L, "Streaming")

        assertNull(LearnedCategoryRules.suggest("Some Totally Unrecognized Merchant", listOf(media)))
    }

    @Test
    fun `learning the same note again replaces the old mapping`() {
        val media = category(4L, "Streaming")
        val furniture = category(5L, "Furniture")
        LearnedCategoryRules.learn("Ikea Slagelse", media.id)
        LearnedCategoryRules.learn("Ikea Slagelse", furniture.id)

        assertEquals(furniture, LearnedCategoryRules.suggest("Ikea Slagelse", listOf(media, furniture)))
    }

    @Test
    fun `a blank note is never learned`() {
        val media = category(6L, "Streaming")
        LearnedCategoryRules.learn("", media.id)
        LearnedCategoryRules.learn("   ", media.id)

        assertNull(LearnedCategoryRules.suggest("", listOf(media)))
        assertNull(LearnedCategoryRules.suggest("   ", listOf(media)))
    }

    @Test
    fun `a learned category id missing from the given list returns null`() {
        val media = category(7L, "Streaming")
        val other = category(8L, "Other")
        LearnedCategoryRules.learn("Youtube Premium", media.id)

        // The caller's own category list no longer has the id that was learned (e.g. deleted).
        assertNull(LearnedCategoryRules.suggest("Youtube Premium", listOf(other)))
    }
}
