package com.financetracker.app

import com.financetracker.app.data.backup.BackupCodec
import com.financetracker.app.data.backup.BackupContents
import com.financetracker.app.data.backup.BackupPasswordRequiredException
import com.financetracker.app.data.backup.InvalidBackupException
import com.financetracker.app.data.backup.WrongBackupPasswordException
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.Transaction
import com.financetracker.app.data.db.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val contents = BackupContents(
        createdAt = 1_790_000_000_000L,
        accounts = listOf(Account(id = 3, name = "Sydbank ••1234", initialBalance = 10.5, currencyCode = "DKK")),
        categories = listOf(
            Category(id = 7, name = "Groceries", mainCategory = "Food", type = TransactionType.EXPENSE, colorHex = "#FF0000")
        ),
        transactions = listOf(
            Transaction(id = 11, amount = 199.95, type = TransactionType.EXPENSE, accountId = 3, categoryId = 7, date = 1_789_000_000_000L, note = "Netto \"æøå\"", createdAt = 5L),
            Transaction(id = 12, amount = 25_000.0, type = TransactionType.INCOME, accountId = 3, categoryId = null, date = 1_789_100_000_000L, note = "", createdAt = 6L)
        ),
        prefs = mapOf(
            "currency_code" to "DKK",
            "main_account_id" to 3L,
            "some_int" to 4,
            "some_float" to 1.5f,
            "exclude_transfers" to true,
            "fixed_category_ids" to setOf("7", "9"),
            "enablebanking_connections" to "[{\"sessionId\":\"abc\"}]"
        ),
        includesBankConnections = true
    )

    @Test
    fun `an unencrypted backup round-trips everything`() {
        val bytes = BackupCodec.encode(contents, null)
        assertFalse(BackupCodec.isEncrypted(bytes))
        assertEquals(contents, BackupCodec.decode(bytes, null))
    }

    @Test
    fun `an encrypted backup round-trips with the right password and hides its contents`() {
        val bytes = BackupCodec.encode(contents, "correct horse".toCharArray())
        assertTrue(BackupCodec.isEncrypted(bytes))
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("Netto"))
        assertEquals(contents, BackupCodec.decode(bytes, "correct horse".toCharArray()))
    }

    @Test(expected = WrongBackupPasswordException::class)
    fun `a wrong password is rejected`() {
        val bytes = BackupCodec.encode(contents, "correct horse".toCharArray())
        BackupCodec.decode(bytes, "battery staple".toCharArray())
    }

    @Test(expected = BackupPasswordRequiredException::class)
    fun `an encrypted backup asks for its password`() {
        BackupCodec.decode(BackupCodec.encode(contents, "pw".toCharArray()), null)
    }

    @Test(expected = InvalidBackupException::class)
    fun `another file is rejected`() {
        BackupCodec.decode("Date;Amount\n2026-01-01;5".toByteArray(), null)
    }
}
