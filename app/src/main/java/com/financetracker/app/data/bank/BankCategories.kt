package com.financetracker.app.data.bank

import com.financetracker.app.data.repository.FinanceRepository

/** Adds whichever [Bank] is selected/connected's starter categories to the database — retroactively
 * fills in any an *existing* install is still missing (exact mainCategory+name+type dedup, the
 * same rule [FinanceRepository.getOrCreateCategory] always uses), never touching a category the
 * user already has. Safe and cheap to call on every app launch and whenever the selected bank
 * changes. */
object BankCategories {

    suspend fun ensure(repository: FinanceRepository, bank: Bank) {
        bank.defaultCategories.forEach { seed ->
            repository.getOrCreateCategory(seed.mainCategory, seed.name, seed.type, seed.colorHex)
        }
    }
}
