package com.financetracker.app.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The account every screen's account selector starts on (Settings > Accounts > "Main
 * account"); null means "All accounts". Only one account can be main at a time. */
object MainAccountSettings {
    private const val PREFS_NAME = "finance_prefs"
    private const val KEY_MAIN_ACCOUNT_ID = "main_account_id"

    private lateinit var prefs: android.content.SharedPreferences
    private val _mainAccountId = MutableStateFlow<Long?>(null)
    val mainAccountId: StateFlow<Long?> get() = _mainAccountId

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _mainAccountId.value = prefs.getLong(KEY_MAIN_ACCOUNT_ID, -1L).takeIf { it >= 0 }
    }

    fun setMainAccount(accountId: Long?) {
        _mainAccountId.value = accountId
        if (::prefs.isInitialized) {
            prefs.edit().apply {
                if (accountId == null) remove(KEY_MAIN_ACCOUNT_ID) else putLong(KEY_MAIN_ACCOUNT_ID, accountId)
            }.apply()
        }
    }

    /** Called when [removedAccountId] is deleted or merged away — the main flag moves to
     * [replacementId] (a merge target) or is cleared, never left pointing at a missing account. */
    fun onAccountRemoved(removedAccountId: Long, replacementId: Long? = null) {
        if (_mainAccountId.value == removedAccountId) setMainAccount(replacementId)
    }
}
