package com.financetracker.app.data.prefs

import android.content.Context
import com.financetracker.app.data.bank.SupportedBanks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** [bankId] identifies which [com.financetracker.app.data.bank.Bank] this account came from —
 * needed once more than one bank can be connected at a time, so e.g. sync's local-account naming
 * can label a Lunar account "Lunar ..." and a Sydbank one "Sydbank ..." instead of guessing from
 * whichever bank happens to be selected elsewhere in the UI. */
data class LinkedBankAccount(
    val uid: String,
    val iban: String?,
    val name: String,
    val product: String?,
    val currency: String,
    val bankId: String
)

/** One independent Enable Banking connection to a single bank: its own session, its own linked
 * accounts and which of those the user has chosen to sync, and its own consent expiry. The app
 * can hold several of these at once (e.g. Sydbank and Lunar side by side, each syncing
 * independently) — see [EnableBankingPrefs.connections]. */
data class BankConnection(
    val bankId: String,
    val sessionId: String,
    val linkedAccounts: List<LinkedBankAccount>,
    val selectedAccountUids: Set<String>,
    val consentValidUntil: Long?
)

/** Which bank a pending `/auth` request was started for, so the redirect it eventually sends
 * back can be filed under the right [BankConnection] once exchanged — the redirect URL itself
 * carries no bank identity, only the `state` nonce and an authorization `code`. */
data class PendingAuth(val state: String, val bankId: String)

/**
 * Persists every Enable Banking connection the app has (one per bank — see [BankConnection]):
 * each one's active session, which accounts came back from it, which of those the user has
 * chosen to sync, and when its consent expires. [lastSyncedAt] and [accountLinkMap] are shared
 * across every connection rather than per-bank, since "last synced" reads naturally as one
 * overall status and account uids are already globally unique regardless of which bank they
 * came from.
 */
object EnableBankingPrefs {
    private const val PREFS_NAME = "finance_prefs"
    private const val KEY_CONNECTIONS = "enablebanking_connections"
    private const val KEY_LAST_SYNCED_AT = "enablebanking_last_synced_at"
    private const val KEY_PENDING_AUTH_STATE = "enablebanking_pending_auth_state"
    private const val KEY_PENDING_AUTH_BANK_ID = "enablebanking_pending_auth_bank_id"
    private const val KEY_ACCOUNT_LINK_MAP = "enablebanking_account_link_map"
    private const val KEY_SELECTED_BANK_ID = "enablebanking_selected_bank_id"

    // Pre-multi-bank-support keys: a single connection lived directly under these instead of
    // inside a KEY_CONNECTIONS list. See migrateLegacySingleConnectionIfNeeded().
    private const val KEY_LEGACY_SESSION_ID = "enablebanking_session_id"
    private const val KEY_LEGACY_LINKED_ACCOUNTS = "enablebanking_linked_accounts"
    private const val KEY_LEGACY_SELECTED_ACCOUNT_UIDS = "enablebanking_selected_account_uids"
    private const val KEY_LEGACY_CONSENT_VALID_UNTIL = "enablebanking_consent_valid_until"

    private lateinit var prefs: android.content.SharedPreferences

    private val _connections = MutableStateFlow<List<BankConnection>>(emptyList())
    val connections: StateFlow<List<BankConnection>> get() = _connections

    private val _lastSyncedAt = MutableStateFlow<Long?>(null)
    val lastSyncedAt: StateFlow<Long?> get() = _lastSyncedAt

    /** Bank account uid -> local [com.financetracker.app.data.db.entity.Account] id, once a sync
     * has resolved which local account a linked bank account maps to. Lets later syncs find the
     * right local account directly instead of re-matching by name every time, so renaming an
     * account in Settings never causes sync to lose track of it and create a duplicate. */
    private val _accountLinkMap = MutableStateFlow<Map<String, Long>>(emptyMap())
    val accountLinkMap: StateFlow<Map<String, Long>> get() = _accountLinkMap

    /** Which [com.financetracker.app.data.bank.Bank] is currently pre-selected in the "connect a
     * bank" picker — defaults to [SupportedBanks.DEFAULT] (Sydbank). Purely a UI convenience, not
     * tied to any particular connection. */
    private val _selectedBankId = MutableStateFlow(SupportedBanks.DEFAULT.id)
    val selectedBankId: StateFlow<String> get() = _selectedBankId

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        migrateLegacySingleConnectionIfNeeded()
        _connections.value = deserializeConnections(prefs.getString(KEY_CONNECTIONS, null))
        _lastSyncedAt.value = prefs.getLong(KEY_LAST_SYNCED_AT, -1L).takeIf { it >= 0 }
        _accountLinkMap.value = deserializeAccountLinkMap(prefs.getString(KEY_ACCOUNT_LINK_MAP, null))
        _selectedBankId.value = prefs.getString(KEY_SELECTED_BANK_ID, null) ?: SupportedBanks.DEFAULT.id
    }

    /** One-time upgrade path for an install from before multi-bank support, which stored its
     * single connection directly under [KEY_LEGACY_SESSION_ID] etc. instead of inside a
     * [KEY_CONNECTIONS] list. Without this, that connection would just silently vanish on first
     * launch after the update — the bank would look disconnected, forcing the user to reconnect,
     * which then mints a fresh session with fresh account uids that [accountLinkMap] (still keyed
     * by the *old* uids) no longer recognizes, risking a duplicate local account being created for
     * one the user already has. Folding the legacy data into the new format up front means the
     * existing connection (and every uid [accountLinkMap] already knows) carries over untouched.
     * A no-op once [KEY_CONNECTIONS] exists, or if there was never a legacy connection at all. */
    private fun migrateLegacySingleConnectionIfNeeded() {
        if (prefs.contains(KEY_CONNECTIONS)) return
        val legacySessionId = prefs.getString(KEY_LEGACY_SESSION_ID, null) ?: return
        val bankId = prefs.getString(KEY_SELECTED_BANK_ID, null) ?: SupportedBanks.DEFAULT.id
        val legacyAccounts = deserializeAccounts(prefs.getString(KEY_LEGACY_LINKED_ACCOUNTS, null))
            .map { it.copy(bankId = bankId) }
        val legacySelectedUids = prefs.getStringSet(KEY_LEGACY_SELECTED_ACCOUNT_UIDS, emptySet()).orEmpty()
        val legacyConsentValidUntil = prefs.getLong(KEY_LEGACY_CONSENT_VALID_UNTIL, -1L).takeIf { it >= 0 }
        val connection = BankConnection(bankId, legacySessionId, legacyAccounts, legacySelectedUids, legacyConsentValidUntil)
        prefs.edit()
            .putString(KEY_CONNECTIONS, serializeConnections(listOf(connection)))
            .remove(KEY_LEGACY_SESSION_ID)
            .remove(KEY_LEGACY_LINKED_ACCOUNTS)
            .remove(KEY_LEGACY_SELECTED_ACCOUNT_UIDS)
            .remove(KEY_LEGACY_CONSENT_VALID_UNTIL)
            .apply()
    }

    fun setSelectedBankId(id: String) {
        _selectedBankId.value = id
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_SELECTED_BANK_ID, id).apply()
        }
    }

    /** Called once a `code` has been exchanged for a live session — either connecting a bank for
     * the very first time, or a later re-auth that's just refreshing that same bank's account
     * list (see [com.financetracker.app.ui.screens.settings.EnableBankingViewModel.startBankAuth],
     * shared by both). Only [bankId]'s own connection is touched — every other connected bank is
     * left exactly as it was. A brand-new account (one this bank returned that we hadn't seen
     * before — most commonly because it was opened after the last connect) is selected by
     * default; an account we already knew about keeps whatever the user last chose for it, so
     * refreshing to pick up a new account never silently re-enables one they'd deliberately
     * unchecked. On a first connect for this bank there's nothing to preserve, so this naturally
     * reduces to "select everything". */
    fun saveConnection(bankId: String, sessionId: String, accounts: List<LinkedBankAccount>, consentValidUntil: Long) {
        val existing = _connections.value.firstOrNull { it.bankId == bankId }
        val accountUids = accounts.map { it.uid }.toSet()
        val previouslyKnownUids = existing?.linkedAccounts?.map { it.uid }?.toSet().orEmpty()
        val newUids = accountUids - previouslyKnownUids
        val selected = (existing?.selectedAccountUids.orEmpty() intersect accountUids) + newUids
        val updated = BankConnection(bankId, sessionId, accounts, selected, consentValidUntil)
        _connections.value = _connections.value.filterNot { it.bankId == bankId } + updated
        persistConnections()
    }

    fun setAccountSelected(bankId: String, uid: String, selected: Boolean) {
        _connections.value = _connections.value.map { connection ->
            if (connection.bankId != bankId) return@map connection
            val uids = if (selected) connection.selectedAccountUids + uid else connection.selectedAccountUids - uid
            connection.copy(selectedAccountUids = uids)
        }
        persistConnections()
    }

    fun setAccountLink(uid: String, accountId: Long) {
        val updated = _accountLinkMap.value + (uid to accountId)
        _accountLinkMap.value = updated
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_ACCOUNT_LINK_MAP, serializeAccountLinkMap(updated)).apply()
        }
    }

    /** Repoints every uid currently mapped to [fromAccountId] onto [toAccountId] — used when two
     * local accounts turn out to be the same real-world one and get merged (see
     * [com.financetracker.app.ui.screens.settings.SettingsViewModel.mergeAccounts]), so a future
     * sync's uid lookup finds the account that was kept instead of recreating the one just
     * merged away. */
    fun remapAccountLink(fromAccountId: Long, toAccountId: Long) {
        val updated = _accountLinkMap.value.mapValues { (_, accountId) ->
            if (accountId == fromAccountId) toAccountId else accountId
        }
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

    /** The `state` value (plus which bank it was for) sent with the last `/auth` request,
     * persisted (not just in-memory) because the app process can be killed while the user is away
     * completing MitID login in the browser. Checked against the value the redirect brings back,
     * then cleared. */
    fun setPendingAuth(state: String, bankId: String) {
        if (::prefs.isInitialized) {
            prefs.edit()
                .putString(KEY_PENDING_AUTH_STATE, state)
                .putString(KEY_PENDING_AUTH_BANK_ID, bankId)
                .apply()
        }
    }

    fun consumePendingAuth(): PendingAuth? {
        if (!::prefs.isInitialized) return null
        val state = prefs.getString(KEY_PENDING_AUTH_STATE, null)
        val bankId = prefs.getString(KEY_PENDING_AUTH_BANK_ID, null)
        prefs.edit().remove(KEY_PENDING_AUTH_STATE).remove(KEY_PENDING_AUTH_BANK_ID).apply()
        if (state == null || bankId == null) return null
        return PendingAuth(state, bankId)
    }

    /** Disconnects only [bankId]'s own connection — every other connected bank keeps syncing.
     * Never touches [accountLinkMap], [lastSyncedAt], or local transaction data: reconnecting
     * this same bank later re-matches its accounts to the same local accounts by name rather than
     * creating duplicates (see [com.financetracker.app.data.enablebanking.EnableBankingSyncCoordinator.resolveLocalAccount]). */
    fun disconnect(bankId: String) {
        _connections.value = _connections.value.filterNot { it.bankId == bankId }
        persistConnections()
    }

    private fun persistConnections() {
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_CONNECTIONS, serializeConnections(_connections.value)).apply()
        }
    }

    private fun serializeConnections(connections: List<BankConnection>): String {
        val array = JSONArray()
        connections.forEach { connection ->
            array.put(
                JSONObject().apply {
                    put("bankId", connection.bankId)
                    put("sessionId", connection.sessionId)
                    put("accounts", serializeAccounts(connection.linkedAccounts))
                    put("selectedAccountUids", JSONArray(connection.selectedAccountUids.toList()))
                    put("consentValidUntil", connection.consentValidUntil ?: JSONObject.NULL)
                }
            )
        }
        return array.toString()
    }

    private fun deserializeConnections(raw: String?): List<BankConnection> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val selectedUids = obj.getJSONArray("selectedAccountUids")
                BankConnection(
                    bankId = obj.getString("bankId"),
                    sessionId = obj.getString("sessionId"),
                    linkedAccounts = deserializeAccounts(obj.getString("accounts")),
                    selectedAccountUids = (0 until selectedUids.length()).map { selectedUids.getString(it) }.toSet(),
                    consentValidUntil = if (obj.isNull("consentValidUntil")) null else obj.getLong("consentValidUntil")
                )
            }
        } catch (e: Exception) {
            emptyList()
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
                    put("bankId", account.bankId)
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
                    currency = obj.optString("currency"),
                    bankId = obj.optString("bankId").ifBlank { SupportedBanks.DEFAULT.id }
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
