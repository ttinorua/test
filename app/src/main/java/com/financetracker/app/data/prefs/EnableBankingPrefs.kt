package com.financetracker.app.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class LinkedBankAccount(
    val uid: String,
    val iban: String?,
    val name: String,
    val product: String?,
    val currency: String
)

/**
 * Persists the state of the Enable Banking (Sydbank) connection: the active session, which
 * accounts came back from that session, which of those the user has chosen to sync, and when
 * the consent expires / was last synced.
 */
object EnableBankingPrefs {
    private const val PREFS_NAME = "finance_prefs"
    private const val KEY_SESSION_ID = "enablebanking_session_id"
    private const val KEY_LINKED_ACCOUNTS = "enablebanking_linked_accounts"
    private const val KEY_SELECTED_ACCOUNT_UIDS = "enablebanking_selected_account_uids"
    private const val KEY_CONSENT_VALID_UNTIL = "enablebanking_consent_valid_until"
    private const val KEY_LAST_SYNCED_AT = "enablebanking_last_synced_at"
    private const val KEY_PENDING_AUTH_STATE = "enablebanking_pending_auth_state"
    private const val KEY_ACCOUNT_LINK_MAP = "enablebanking_account_link_map"

    private lateinit var prefs: android.content.SharedPreferences

    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> get() = _sessionId

    private val _linkedAccounts = MutableStateFlow<List<LinkedBankAccount>>(emptyList())
    val linkedAccounts: StateFlow<List<LinkedBankAccount>> get() = _linkedAccounts

    private val _selectedAccountUids = MutableStateFlow<Set<String>>(emptySet())
    val selectedAccountUids: StateFlow<Set<String>> get() = _selectedAccountUids

    private val _consentValidUntil = MutableStateFlow<Long?>(null)
    val consentValidUntil: StateFlow<Long?> get() = _consentValidUntil

    private val _lastSyncedAt = MutableStateFlow<Long?>(null)
    val lastSyncedAt: StateFlow<Long?> get() = _lastSyncedAt

    /** Bank account uid -> local [com.financetracker.app.data.db.entity.Account] id, once a sync
     * has resolved which local account a linked bank account maps to. Lets later syncs find the
     * right local account directly instead of re-matching by name every time, so renaming an
     * account in Settings never causes sync to lose track of it and create a duplicate. */
    private val _accountLinkMap = MutableStateFlow<Map<String, Long>>(emptyMap())
    val accountLinkMap: StateFlow<Map<String, Long>> get() = _accountLinkMap

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _sessionId.value = prefs.getString(KEY_SESSION_ID, null)
        _linkedAccounts.value = deserializeAccounts(prefs.getString(KEY_LINKED_ACCOUNTS, null))
        _selectedAccountUids.value = prefs.getStringSet(KEY_SELECTED_ACCOUNT_UIDS, emptySet()).orEmpty()
        _consentValidUntil.value = prefs.getLong(KEY_CONSENT_VALID_UNTIL, -1L).takeIf { it >= 0 }
        _lastSyncedAt.value = prefs.getLong(KEY_LAST_SYNCED_AT, -1L).takeIf { it >= 0 }
        _accountLinkMap.value = deserializeAccountLinkMap(prefs.getString(KEY_ACCOUNT_LINK_MAP, null))
    }

    /** Called once a `code` has been exchanged for a live session. Selects all returned
     * accounts by default; the user can narrow that down afterward. */
    fun saveConnection(sessionId: String, accounts: List<LinkedBankAccount>, consentValidUntil: Long) {
        val selected = accounts.map { it.uid }.toSet()
        _sessionId.value = sessionId
        _linkedAccounts.value = accounts
        _selectedAccountUids.value = selected
        _consentValidUntil.value = consentValidUntil
        if (::prefs.isInitialized) {
            prefs.edit()
                .putString(KEY_SESSION_ID, sessionId)
                .putString(KEY_LINKED_ACCOUNTS, serializeAccounts(accounts))
                .putStringSet(KEY_SELECTED_ACCOUNT_UIDS, selected)
                .putLong(KEY_CONSENT_VALID_UNTIL, consentValidUntil)
                .apply()
        }
    }

    fun setSelectedAccountUids(uids: Set<String>) {
        _selectedAccountUids.value = uids
        if (::prefs.isInitialized) {
            prefs.edit().putStringSet(KEY_SELECTED_ACCOUNT_UIDS, uids).apply()
        }
    }

    fun setAccountLink(uid: String, accountId: Long) {
        val updated = _accountLinkMap.value + (uid to accountId)
        _accountLinkMap.value = updated
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_ACCOUNT_LINK_MAP, serializeAccountLinkMap(updated)).apply()
        }
    }

    fun setLastSyncedAt(timestamp: Long) {
        _lastSyncedAt.value = timestamp
        if (::prefs.isInitialized) {
            prefs.edit().putLong(KEY_LAST_SYNCED_AT, timestamp).apply()
        }
    }

    /** The `state` value sent with the last /auth request, persisted (not just in-memory)
     * because the app process can be killed while the user is away completing MitID login
     * in the browser. Checked against the value the redirect brings back, then cleared. */
    fun setPendingAuthState(state: String?) {
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_PENDING_AUTH_STATE, state).apply()
        }
    }

    fun consumePendingAuthState(): String? {
        if (!::prefs.isInitialized) return null
        val value = prefs.getString(KEY_PENDING_AUTH_STATE, null)
        prefs.edit().remove(KEY_PENDING_AUTH_STATE).apply()
        return value
    }

    fun disconnect() {
        _sessionId.value = null
        _linkedAccounts.value = emptyList()
        _selectedAccountUids.value = emptySet()
        _consentValidUntil.value = null
        _lastSyncedAt.value = null
        _accountLinkMap.value = emptyMap()
        if (::prefs.isInitialized) {
            prefs.edit()
                .remove(KEY_SESSION_ID)
                .remove(KEY_LINKED_ACCOUNTS)
                .remove(KEY_SELECTED_ACCOUNT_UIDS)
                .remove(KEY_CONSENT_VALID_UNTIL)
                .remove(KEY_LAST_SYNCED_AT)
                .remove(KEY_ACCOUNT_LINK_MAP)
                .apply()
        }
    }

    private fun serializeAccounts(accounts: List<LinkedBankAccount>): String {
        val array = JSONArray()
        accounts.forEach { account ->
            array.put(
                JSONObject().apply {
                    put("uid", account.uid)
                    put("iban", account.iban ?: JSONObject.NULL)
                    put("name", account.name)
                    put("product", account.product ?: JSONObject.NULL)
                    put("currency", account.currency)
                }
            )
        }
        return array.toString()
    }

    private fun deserializeAccounts(raw: String?): List<LinkedBankAccount> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                LinkedBankAccount(
                    uid = obj.getString("uid"),
                    iban = if (obj.isNull("iban")) null else obj.getString("iban"),
                    name = obj.optString("name"),
                    product = if (obj.isNull("product")) null else obj.getString("product"),
                    currency = obj.optString("currency")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeAccountLinkMap(map: Map<String, Long>): String {
        val obj = JSONObject()
        map.forEach { (uid, accountId) -> obj.put(uid, accountId) }
        return obj.toString()
    }

    private fun deserializeAccountLinkMap(raw: String?): Map<String, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associateWith { obj.getLong(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
