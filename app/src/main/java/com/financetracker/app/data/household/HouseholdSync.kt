package com.financetracker.app.data.household

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.financetracker.app.BuildConfig
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.prefs.EnableBankingPrefs
import com.financetracker.app.data.repository.FinanceRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Everything the household screens show. */
data class HouseholdState(
    /** False when the build has no Firebase project (FIREBASE_* settings). */
    val configured: Boolean = false,
    val householdId: String? = null,
    val myUid: String? = null,
    val myName: String = "",
    /** The invite waiting for a partner: its code and when it expires. */
    val inviteCode: String? = null,
    val inviteExpiresAt: Long = 0L,
    val members: List<HouseholdMember> = emptyList(),
    val lines: List<HouseholdBudgetLine> = emptyList(),
    val reports: List<MemberReport> = emptyList(),
    val includedAccountIds: Set<Long> = emptySet(),
    val shareDetails: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null
) {
    val joined: Boolean get() = householdId != null
    val partner: HouseholdMember? get() = members.firstOrNull { it.uid != myUid }
    val partnerName: String get() = partner?.name?.ifBlank { null } ?: "Partner"
    val partnerUpdatedAt: Long? get() = reports.filter { it.uid != myUid }.maxOfOrNull { it.updatedAt }
    fun memberName(uid: String) = members.firstOrNull { it.uid == uid }?.name ?: "Partner"
}

/**
 * The household budget shared with a partner, live through the app's Firebase project. Each phone
 * signs in anonymously (no account or password). Households, budget lines, members and monthly
 * spending live in Firestore, and everything except who-belongs-to-which-household is encrypted
 * with the household key ([HouseholdCrypto]) before it leaves the phone.
 *
 * Each budget line is its own document, so the two of you can change different lines at the same
 * time without either change being lost; each phone writes only its own spending report.
 */
@OptIn(FlowPreview::class)
object HouseholdSync {
    private const val TAG = "HouseholdSync"
    private const val PREFS = "household_prefs"
    private const val INVITE_HOURS = 48L

    private lateinit var prefs: SharedPreferences
    private lateinit var repository: FinanceRepository
    private var auth: FirebaseAuth? = null
    private var db: FirebaseFirestore? = null
    private var key: ByteArray? = null
    private val listeners = mutableListOf<ListenerRegistration>()
    private val published = mutableMapOf<String, Int>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(HouseholdState())
    val state: StateFlow<HouseholdState> = _state.asStateFlow()

    val isConfigured: Boolean
        get() = BuildConfig.FIREBASE_API_KEY.isNotBlank() && BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()

    fun init(context: Context, repository: FinanceRepository) {
        this.repository = repository
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _state.value = HouseholdState(
            configured = isConfigured,
            householdId = prefs.getString("household_id", null),
            myName = prefs.getString("my_name", "").orEmpty(),
            inviteCode = prefs.getString("invite_code", null),
            inviteExpiresAt = prefs.getLong("invite_expires", 0L),
            includedAccountIds = prefs.getStringSet("included_accounts", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
            shareDetails = prefs.getBoolean("share_details", true)
        )
        if (!isConfigured) return
        runCatching {
            val options = FirebaseOptions.Builder()
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .build()
            val app = FirebaseApp.getApps(context).firstOrNull { it.name == "household" }
                ?: FirebaseApp.initializeApp(context.applicationContext, options, "household")
            auth = FirebaseAuth.getInstance(app)
            db = FirebaseFirestore.getInstance(app)
        }.onFailure { Log.w(TAG, "Firebase setup failed", it); return }

        key = prefs.getString("household_key", null)?.let(HouseholdCrypto::unb64)
        _state.update { it.copy(myUid = auth?.currentUser?.uid) }
        if (_state.value.joined) scope.launch { runCatching { signIn(); listen() }.onFailure { Log.w(TAG, "listen", it) } }

        // Keep your share up to date: after a bank sync or any change to transactions, the budget
        // lines or your sharing choices.
        scope.launch {
            combine(
                repository.observeTransactions(),
                state.map { listOf(it.householdId, it.lines.map { l -> l.mainCategory }.sorted(), it.includedAccountIds, it.shareDetails) }
                    .distinctUntilChanged()
            ) { _, _ -> Unit }
                .debounce(2_000)
                .collect { runCatching { publish() }.onFailure { Log.w(TAG, "publish", it) } }
        }
    }

    private suspend fun signIn(): String {
        val a = auth ?: error("The household isn't set up in this version of the app.")
        val uid = a.currentUser?.uid ?: a.signInAnonymously().await().user?.uid ?: error("Couldn't sign in.")
        _state.update { it.copy(myUid = uid) }
        return uid
    }

    private fun firestore() = db ?: error("The household isn't set up in this version of the app.")

    private suspend fun <T> busy(block: suspend () -> T): Result<T> {
        _state.update { it.copy(busy = true, error = null) }
        val result = runCatching { block() }
        _state.update { it.copy(busy = false, error = result.exceptionOrNull()?.let(::friendly)) }
        return result
    }

    private fun friendly(e: Throwable): String = when {
        e.message?.contains("PERMISSION_DENIED", true) == true -> "Not allowed — the code may have expired or already been used."
        e.message?.contains("network", true) == true || e.message?.contains("UNAVAILABLE") == true -> "No connection. Try again when you're online."
        else -> e.message ?: "Something went wrong."
    }

    // ---- Start, join, leave -------------------------------------------------------------------

    /** Starts a household with you as its only member, and makes an invite code. */
    suspend fun create(name: String): Result<String> = busy {
        val uid = signIn()
        val fs = firestore()
        val householdKey = HouseholdCrypto.newHouseholdKey()
        val ref = fs.collection("households").document()
        val code = HouseholdCrypto.newInviteCode()
        val expires = System.currentTimeMillis() + INVITE_HOURS * 3_600_000
        fs.batch().apply {
            set(ref, mapOf("members" to listOf(uid), "createdBy" to uid, "createdAt" to FieldValue.serverTimestamp()))
            set(ref.collection("members").document(uid), memberDoc(householdKey, name))
            set(fs.collection("invites").document(HouseholdCrypto.inviteId(code)), inviteDoc(ref.id, householdKey, code, expires))
        }.commit().await()
        saveJoined(ref.id, householdKey, name)
        saveInvite(code, expires)
        listen()
        code
    }

    /** A new invite code (e.g. after the last one expired, or for a new partner). */
    suspend fun newInvite(): Result<String> = busy {
        signIn()
        val fs = firestore()
        val id = _state.value.householdId ?: error("Not in a household.")
        val householdKey = key ?: error("Not in a household.")
        _state.value.inviteCode?.let { old -> runCatching { fs.collection("invites").document(HouseholdCrypto.inviteId(old)).delete().await() } }
        val code = HouseholdCrypto.newInviteCode()
        val expires = System.currentTimeMillis() + INVITE_HOURS * 3_600_000
        fs.collection("invites").document(HouseholdCrypto.inviteId(code)).set(inviteDoc(id, householdKey, code, expires)).await()
        saveInvite(code, expires)
        code
    }

    /** Joins your partner's household with the code they gave you. */
    suspend fun join(code: String, name: String): Result<Unit> = busy {
        val uid = signIn()
        val fs = firestore()
        val inviteId = HouseholdCrypto.inviteId(code)
        val invite = fs.collection("invites").document(inviteId).get().await()
        if (!invite.exists()) error("That code doesn't exist. Check it, or ask your partner for a new one.")
        val expires = (invite.get("expiresAt") as? Timestamp)?.toDate()?.time ?: 0L
        if (expires < System.currentTimeMillis()) error("That code has expired. Ask your partner for a new one.")
        val householdId = invite.getString("householdId") ?: error("That code is broken.")
        val householdKey = HouseholdCrypto.unwrapKey(
            WrappedKey(invite.getString("salt").orEmpty(), invite.getString("key").orEmpty()), code
        ) ?: error("That code doesn't fit. Check it, or ask your partner for a new one.")
        val ref = fs.collection("households").document(householdId)
        fs.batch().apply {
            update(ref, mapOf("members" to FieldValue.arrayUnion(uid), "joinedWith" to inviteId))
            set(ref.collection("members").document(uid), memberDoc(householdKey, name))
            delete(invite.reference)
        }.commit().await()
        saveJoined(householdId, householdKey, name)
        listen()
    }

    /** Leaves the household: your name and spending are removed from it; the last one to leave
     * removes it altogether. Your own data on this phone isn't touched. */
    suspend fun leave(): Result<Unit> = busy {
        val uid = signIn()
        val fs = firestore()
        val id = _state.value.householdId ?: return@busy
        val ref = fs.collection("households").document(id)
        stopListening()
        runCatching {
            val alone = _state.value.members.none { it.uid != uid }
            val mine = ref.collection("spending").get().await().documents.filter { it.id.startsWith(uid + "_") }
            fs.batch().apply {
                mine.forEach { delete(it.reference) }
                delete(ref.collection("members").document(uid))
                _state.value.inviteCode?.let { delete(fs.collection("invites").document(HouseholdCrypto.inviteId(it))) }
            }.commit().await()
            if (alone) {
                ref.collection("budget").get().await().documents.forEach { it.reference.delete().await() }
                ref.delete().await()
            } else {
                ref.update("members", FieldValue.arrayRemove(uid)).await()
            }
        }.onFailure { Log.w(TAG, "leave", it) }
        key = null
        published.clear()
        prefs.edit().remove("household_id").remove("household_key").remove("invite_code").remove("invite_expires").apply()
        _state.update { it.copy(householdId = null, inviteCode = null, members = emptyList(), lines = emptyList(), reports = emptyList()) }
    }

    private fun saveJoined(id: String, householdKey: ByteArray, name: String) {
        key = householdKey
        prefs.edit().putString("household_id", id).putString("household_key", HouseholdCrypto.b64(householdKey)).putString("my_name", name).apply()
        _state.update { it.copy(householdId = id, myName = name) }
    }

    private fun saveInvite(code: String, expires: Long) {
        prefs.edit().putString("invite_code", code).putLong("invite_expires", expires).apply()
        _state.update { it.copy(inviteCode = code, inviteExpiresAt = expires) }
    }

    private fun memberDoc(householdKey: ByteArray, name: String) = mapOf(
        "data" to HouseholdCrypto.encryptText(householdKey, JSONObject().put("name", name).put("details", _state.value.shareDetails).toString()),
        "updatedAt" to System.currentTimeMillis()
    )

    private fun inviteDoc(householdId: String, householdKey: ByteArray, code: String, expires: Long): Map<String, Any> {
        val wrapped = HouseholdCrypto.wrapKey(householdKey, code)
        return mapOf("householdId" to householdId, "salt" to wrapped.salt, "key" to wrapped.data, "expiresAt" to Timestamp(Date(expires)))
    }

    // ---- Your choices ---------------------------------------------------------------------------

    fun setIncludedAccounts(ids: Set<Long>) {
        prefs.edit().putStringSet("included_accounts", ids.map { it.toString() }.toSet()).apply()
        _state.update { it.copy(includedAccountIds = ids) }
    }

    fun setShareDetails(share: Boolean) {
        prefs.edit().putBoolean("share_details", share).apply()
        _state.update { it.copy(shareDetails = share) }
        scope.launch { runCatching { updateMemberDoc() } }
    }

    private suspend fun updateMemberDoc() {
        val uid = signIn()
        val id = _state.value.householdId ?: return
        val householdKey = key ?: return
        firestore().collection("households").document(id).collection("members").document(uid)
            .set(memberDoc(householdKey, _state.value.myName)).await()
    }

    // ---- The shared budget ----------------------------------------------------------------------

    private fun lineId(householdKey: ByteArray, mainCategory: String) = HouseholdCrypto.tag(householdKey, "line:" + mainCategory.trim().lowercase())

    /** Sets one line's monthly limit (adding the line if it's new). */
    suspend fun setLine(mainCategory: String, limit: Double): Result<Unit> = busy {
        val uid = signIn()
        val id = _state.value.householdId ?: error("Not in a household.")
        val householdKey = key ?: error("Not in a household.")
        firestore().collection("households").document(id).collection("budget").document(lineId(householdKey, mainCategory)).set(
            mapOf(
                "data" to HouseholdCrypto.encryptText(householdKey, JSONObject().put("main", mainCategory).put("limit", limit).toString()),
                "updatedBy" to uid,
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    suspend fun removeLine(mainCategory: String): Result<Unit> = busy {
        signIn()
        val id = _state.value.householdId ?: error("Not in a household.")
        val householdKey = key ?: error("Not in a household.")
        firestore().collection("households").document(id).collection("budget").document(lineId(householdKey, mainCategory)).delete().await()
    }

    // ---- Live updates ---------------------------------------------------------------------------

    private fun stopListening() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    private fun listen() {
        stopListening()
        val id = _state.value.householdId ?: return
        val householdKey = key ?: return
        val ref = firestore().collection("households").document(id)

        listeners += ref.addSnapshotListener { snap, e ->
            if (e != null) { Log.w(TAG, "household", e); return@addSnapshotListener }
            @Suppress("UNCHECKED_CAST")
            val members = (snap?.get("members") as? List<String>).orEmpty()
            // Once a partner has joined, the invite is used up.
            if (members.size > 1 && _state.value.inviteCode != null) {
                prefs.edit().remove("invite_code").remove("invite_expires").apply()
                _state.update { it.copy(inviteCode = null) }
            }
            _state.update { s -> s.copy(members = s.members.filter { it.uid in members }) }
        }
        listeners += ref.collection("members").addSnapshotListener { snap, e ->
            if (e != null || snap == null) return@addSnapshotListener
            val members = snap.documents.mapNotNull { doc ->
                runCatching {
                    val json = JSONObject(HouseholdCrypto.decryptText(householdKey, doc.getString("data").orEmpty()))
                    HouseholdMember(doc.id, json.optString("name"), json.optBoolean("details", true))
                }.getOrNull()
            }
            _state.update { it.copy(members = members) }
        }
        listeners += ref.collection("budget").addSnapshotListener { snap, e ->
            if (e != null || snap == null) return@addSnapshotListener
            val lines = snap.documents.mapNotNull { doc ->
                runCatching {
                    val json = JSONObject(HouseholdCrypto.decryptText(householdKey, doc.getString("data").orEmpty()))
                    HouseholdBudgetLine(doc.id, json.getString("main"), json.getDouble("limit"), doc.getString("updatedBy").orEmpty(), doc.getLong("updatedAt") ?: 0L)
                }.getOrNull()
            }.sortedBy { it.mainCategory.lowercase() }
            _state.update { it.copy(lines = lines) }
        }
        listeners += ref.collection("spending").addSnapshotListener { snap, e ->
            if (e != null || snap == null) return@addSnapshotListener
            val reports = snap.documents.mapNotNull { doc -> runCatching { readReport(householdKey, doc.id, doc.getString("data").orEmpty(), doc.getLong("updatedAt") ?: 0L) }.getOrNull() }
            _state.update { it.copy(reports = reports) }
        }
    }

    // ---- Your share -----------------------------------------------------------------------------

    fun monthKey(time: Long): String = SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(time)

    /** Writes this month's and last month's spending in the household's categories, from the
     * accounts you chose. Skips the write when nothing changed. */
    private suspend fun publish() {
        val s = _state.value
        val id = s.householdId ?: return
        val householdKey = key ?: return
        val uid = signIn()
        val mains = s.lines.map { it.mainCategory }.toSet()
        val now = System.currentTimeMillis()
        val lastMonth = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = now
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            add(java.util.Calendar.MONTH, -1)
        }.timeInMillis
        val months = listOf(monthKey(now), monthKey(lastMonth))
        val ibanByAccount = EnableBankingPrefs.ibanLinkMap.value.entries.associate { (iban, accountId) -> accountId to iban }
        val transactions = repository.observeTransactions().first().filter {
            it.type == TransactionType.EXPENSE && it.accountId in s.includedAccountIds && (it.mainCategoryName ?: "") in mains
        }
        val spending = firestore().collection("households").document(id).collection("spending")
        months.forEach { month ->
            val inMonth = transactions.filter { monthKey(it.date) == month }
            fun accountTag(accountId: Long) = HouseholdCrypto.tag(householdKey, ibanByAccount[accountId] ?: "local:$uid:$accountId")
            val totals = JSONArray()
            inMonth.groupBy { accountTag(it.accountId) to it.mainCategoryName.orEmpty() }.forEach { (k, list) ->
                totals.put(JSONArray().put(k.first).put(k.second).put(list.sumOf { it.amount }))
            }
            val json = JSONObject().put("month", month).put("totals", totals)
            if (s.shareDetails) {
                val txs = JSONArray()
                inMonth.forEach { tx ->
                    txs.put(
                        JSONObject().put("id", tx.id.toString()).put("a", accountTag(tx.accountId)).put("m", tx.mainCategoryName.orEmpty())
                            .put("c", tx.categoryName.orEmpty()).put("v", tx.amount).put("d", tx.date).put("t", tx.note)
                    )
                }
                json.put("tx", txs)
            }
            val docId = "${uid}_$month"
            val text = json.toString()
            if (published[docId] == text.hashCode()) return@forEach
            spending.document(docId).set(mapOf("data" to HouseholdCrypto.encryptText(householdKey, text), "updatedAt" to now)).await()
            published[docId] = text.hashCode()
        }
    }

    private fun readReport(householdKey: ByteArray, docId: String, sealed: String, updatedAt: Long): MemberReport {
        val json = JSONObject(HouseholdCrypto.decryptText(householdKey, sealed))
        val totals = json.getJSONArray("totals").let { arr ->
            (0 until arr.length()).map { i -> arr.getJSONArray(i).let { AccountTotal(it.getString(0), it.getString(1), it.getDouble(2)) } }
        }
        val transactions = json.optJSONArray("tx")?.let { arr ->
            (0 until arr.length()).map { i ->
                arr.getJSONObject(i).let {
                    SharedTransaction(it.getString("id"), it.getString("a"), it.getString("m"), it.getString("c"), it.getDouble("v"), it.getLong("d"), it.optString("t"))
                }
            }
        }
        return MemberReport(docId.substringBefore('_'), json.getString("month"), totals, transactions, updatedAt)
    }
}
