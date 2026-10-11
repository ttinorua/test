package com.financetracker.app.ui.screens.receipts

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.prefs.CurrencySettings
import com.financetracker.app.data.repository.FinanceRepository
import com.financetracker.app.data.stores.Store
import com.financetracker.app.data.stores.StoreAccount
import com.financetracker.app.data.stores.StoreAccounts
import com.financetracker.app.data.stores.StoreConnector
import com.financetracker.app.data.stores.StoreConsents
import com.financetracker.app.data.stores.StoreSync
import com.financetracker.app.ui.components.GlassCard
import com.financetracker.app.ui.components.StoreLogo
import com.financetracker.app.ui.theme.IncomeGreen
import com.financetracker.app.util.Formatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A store's colours and short mark, for its tile. */
private fun brand(store: Store): Triple<Color, Color, String> = when (store) {
    Store.LIDL -> Triple(Color(0xFF0050AA), Color(0xFFFFF000), "L+")
    Store.NEMLIG -> Triple(Color(0xFFE8642C), Color.White, "n")
    Store.COOP -> Triple(Color(0xFFE2001A), Color.White, "coop")
}

@Composable
private fun StoreTile(background: Color, foreground: Color, mark: String) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(mark, color = foreground, fontWeight = FontWeight.Bold, fontSize = if (mark.length > 2) 11.sp else 16.sp)
    }
}

@Composable
private fun StoreTile(store: Store) {
    val (bg, fg, mark) = brand(store)
    StoreLogo(store.company, 40.dp) { StoreTile(bg, fg, mark) }
}

/** Settings for store accounts: each chain on its own row with its connected accounts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreConnectionsScreen(repository: FinanceRepository, onBack: () -> Unit, onConnect: (Store, Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { StoreAccounts.load(context); StoreConsents.load(context) }
    val accounts by StoreAccounts.accounts.collectAsState()
    val consents by StoreConsents.consents.collectAsState()
    val running by StoreSync.running.collectAsState()
    var managing by remember { mutableStateOf<StoreAccount?>(null) }
    var disconnecting by remember { mutableStateOf<StoreAccount?>(null) }
    var clearConsents by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect your stores") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val connected = Store.entries.filter { s -> accounts.any { it.store == s } }
            connected.forEach { store ->
                item(key = "c_${store.id}") {
                    GlassCard {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            StoreTile(store)
                            Text(store.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                            Text(
                                "✓ Connected",
                                color = IncomeGreen,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(IncomeGreen.copy(alpha = .15f)).padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        accounts.filter { it.store == store }.forEach { account ->
                            HorizontalDivider()
                            Row(
                                Modifier.fillMaxWidth().clickable { managing = account }.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(account.name, style = MaterialTheme.typography.bodyLarge)
                                    val status = when {
                                        account.id in running -> "Fetching receipts…"
                                        account.problem != null -> account.problem
                                        account.lastSyncAt == 0L -> "Waiting to fetch"
                                        else -> (if (account.lastReceiptAt > 0) "Latest ${Formatters.date(account.lastReceiptAt)} · " else "") +
                                            "synced ${Formatters.shortWhen(account.lastSyncAt)}"
                                    }
                                    Text(
                                        status,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (account.problem != null && account.id !in running) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (account.id in running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text("${account.receiptCount}", style = MaterialTheme.typography.titleMedium)
                                    Text("receipts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()
                        TextButton(onClick = { onConnect(store, true) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Add another (e.g. your partner's)")
                        }
                    }
                }
            }
            item {
                Text(
                    "ADD CONNECTION",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Store.entries.filter { s -> accounts.none { it.store == s } }.forEach { store ->
                item(key = "a_${store.id}") {
                    GlassCard {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            StoreTile(store)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(store.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (store.available) store.loginNote else "Coming later — share receipts from the ${store.label} app for now",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (store.available) OutlinedButton(onClick = { onConnect(store, false) }) { Text("Connect") }
                        }
                    }
                }
            }
            item {
                GlassCard {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        StoreLogo("Netto", 40.dp) { StoreTile(Color(0xFF0B3D91), Color.White, "N+") }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("Netto+, Bilka+, Føtex+", style = MaterialTheme.typography.titleMedium)
                            Text("Coming later — share receipts from the app for now", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Text(
                    "REMA 1000 can't be connected — share or photograph its receipts. You sign in on each store's own page; " +
                        "the app keeps the store's sign-in (encrypted on this phone), never your password. Stores can change " +
                        "their systems, so a connection may need signing in again now and then.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (consents.isNotEmpty()) {
                item {
                    Column(Modifier.padding(top = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "YOUR CONSENTS",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { clearConsents = true }) { Text("Clear") }
                        }
                        GlassCard {
                            consents.sortedByDescending { it.grantedAt }.forEachIndexed { i, consent ->
                                if (i > 0) HorizontalDivider()
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            consent.store.label + if (consent.forAnother) " · another account" else "",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            "Agreed ${Formatters.syncTimestamp(consent.grantedAt)} · terms v${consent.textVersion}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    managing?.let { account ->
        AlertDialog(
            onDismissRequest = { managing = null },
            title = { Text(account.name) },
            text = {
                Text(
                    "${account.receiptCount} receipts fetched" +
                        (if (account.lastSyncAt > 0) ", last ${Formatters.syncTimestamp(account.lastSyncAt)}" else "") +
                        ". Receipts are fetched again after every bank sync. When you disconnect, you choose whether to keep or delete the receipts already fetched."
                )
            },
            confirmButton = {
                if (account.problem == "Sign in again") {
                    TextButton(onClick = {
                        managing = null
                        StoreAccounts.remove(context, account.id)
                        onConnect(account.store, false)
                    }) { Text("Sign in again") }
                } else {
                    TextButton(onClick = {
                        managing = null
                        StoreSync.enqueue(context, account.id)
                    }) { Text("Fetch now") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    disconnecting = account
                    managing = null
                }) { Text("Disconnect") }
            }
        )
    }

    disconnecting?.let { account ->
        // Receipts are stored per chain, not per account, so they can only be told apart (and
        // offered for deletion) when this is the last account of that store.
        val lastOfStore = accounts.count { it.store == account.store } <= 1
        val close = { disconnecting = null }
        val disconnect = { StoreAccounts.remove(context, account.id) }
        if (lastOfStore) {
            AlertDialog(
                onDismissRequest = close,
                title = { Text("Disconnect ${account.store.label}?") },
                text = { Text("Keep the ${account.receiptCount} receipts already fetched, or delete them too? Either way, no more receipts are fetched.") },
                confirmButton = {
                    TextButton(onClick = { close(); disconnect() }) { Text("Keep receipts") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        close()
                        disconnect()
                        scope.launch { repository.deleteReceiptsFromSource(account.store.id) }
                    }) { Text("Delete receipts") }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = close,
                title = { Text("Disconnect this account?") },
                text = { Text("The fetched receipts stay, because another ${account.store.label} account is still connected and shares them. No more receipts are fetched for this account.") },
                confirmButton = { TextButton(onClick = { close(); disconnect() }) { Text("Disconnect") } },
                dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
            )
        }
    }

    if (clearConsents) {
        AlertDialog(
            onDismissRequest = { clearConsents = false },
            title = { Text("Clear consent records?") },
            text = { Text("This removes the record of when you agreed to each store connection. It doesn't disconnect anything.") },
            confirmButton = {
                TextButton(onClick = {
                    clearConsents = false
                    StoreConsents.clear(context)
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { clearConsents = false }) { Text("Cancel") } }
        )
    }
}

enum class ConnectStep { INTRO, CONSENT, LOGIN, FINISHING, DONE }

class StoreConnectViewModel(
    private val repository: FinanceRepository,
    private val context: Context,
    val store: Store,
    private val forAnother: Boolean
) : ViewModel() {
    val connector: StoreConnector? = StoreConnector.forStore(store)

    private val _step = MutableStateFlow(ConnectStep.INTRO)
    val step: StateFlow<ConnectStep> = _step
    private val _spent = MutableStateFlow<Double?>(null)
    /** Spent at this chain in the last 12 months, from the bank transactions. */
    val spent: StateFlow<Double?> = _spent
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        viewModelScope.launch {
            val since = System.currentTimeMillis() - 365L * 86_400_000L
            _spent.value = repository.observeTransactions().first()
                .filter { tx -> tx.type == TransactionType.EXPENSE && tx.date >= since && store.bankWords.any { tx.note.contains(it, ignoreCase = true) } }
                .sumOf { it.amount }
        }
    }

    fun go(step: ConnectStep) {
        _error.value = null
        _step.value = step
    }

    /** The login window got what the store hands back after signing in. */
    fun captured(value: String) {
        if (_step.value != ConnectStep.LOGIN) return
        _step.value = ConnectStep.FINISHING
        viewModelScope.launch {
            runCatching { connector!!.finishLogin(value) }
                .onSuccess { secret ->
                    // Record the consent (time, store, text version, own/partner) as proof, then
                    // save the account and start fetching.
                    StoreConsents.record(context, store, forAnother)
                    val account = StoreAccounts.add(context, store, secret)
                    StoreSync.enqueue(context, account.id)
                    _step.value = ConnectStep.DONE
                }
                .onFailure {
                    _error.value = "Couldn't finish signing in to ${store.label}: ${it.message ?: "no connection"}"
                    _step.value = ConnectStep.CONSENT
                }
            clearCookies()
        }
    }

    companion object {
        /** The login window starts signed out (so a second account can sign in) and keeps no
         * store cookies afterwards: what the app keeps is stored encrypted instead. */
        fun clearCookies() {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }
}

/** Connecting a store: what you get, what happens (with your consent), then the store's own
 * login page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreConnectScreen(viewModel: StoreConnectViewModel, another: Boolean, onClose: () -> Unit) {
    val step by viewModel.step.collectAsState()
    val spent by viewModel.spent.collectAsState()
    val error by viewModel.error.collectAsState()
    val currency by CurrencySettings.currencyCode.collectAsState()
    val store = viewModel.store
    var agreed by remember { mutableStateOf(false) }

    LaunchedEffect(step) { if (step == ConnectStep.DONE) onClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (step) {
                            ConnectStep.INTRO -> ""
                            ConnectStep.CONSENT -> "Connect ${store.label}"
                            else -> "${store.company} login"
                        }
                    )
                },
                navigationIcon = {
                    if (step == ConnectStep.CONSENT) {
                        IconButton(onClick = { viewModel.go(ConnectStep.INTRO) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    } else {
                        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                    }
                }
            )
        }
    ) { padding ->
        when (step) {
            ConnectStep.INTRO -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StoreTile(store)
                Spacer(Modifier.height(16.dp))
                Text("Connect ${store.label}", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                val amount = spent
                if (amount != null && amount > 0) {
                    Text(
                        "You've spent ${Formatters.currency(amount, currency)} at ${store.company} in the last 12 months",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))
                Benefit("See receipts in the app", "Every item, split into your categories")
                Benefit("Budgets that know what you bought", "Food, household and sweets counted separately")
                Benefit("Price changes", "Know when your usual items get more expensive")
                Spacer(Modifier.height(20.dp))
                Button(onClick = { viewModel.go(ConnectStep.CONSENT) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Yes, connect")
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 8.dp).size(18.dp))
                }
                TextButton(onClick = onClose) { Text("I don't have an account") }
            }
            ConnectStep.CONSENT -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("What happens", style = MaterialTheme.typography.titleLarge)
                Fact("What is fetched", "Your receipts: items, amounts, discounts, store and date. Never payment details or your password.")
                Fact(
                    "How you sign in",
                    "On ${store.company}'s own login page, in a secure window. The app only receives the sign-in that ${store.company} hands back, not your password."
                )
                Fact("When", "The last 12 months now, then new receipts after each bank sync (about every 6 hours).")
                Fact("Where it's kept", "Only on your phone; the sign-in is encrypted. Disconnect any time in Spending → Shopping → stores.")
                Fact(
                    "Good to know",
                    "This uses ${store.company}'s service unofficially, which their terms don't allow, so it can stop working if they change it."
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                GlassCard {
                    Row(Modifier.clickable { agreed = !agreed }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = agreed, onCheckedChange = { agreed = it })
                        Text(
                            if (another) "I have the account owner's permission, and they want their receipts fetched as described."
                            else "This ${store.label} account is mine, and I want my receipts fetched as described.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Button(
                    onClick = {
                        StoreConnectViewModel.clearCookies()
                        viewModel.go(ConnectStep.LOGIN)
                    },
                    enabled = agreed,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue to ${store.company} login")
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 8.dp).size(18.dp))
                }
            }
            ConnectStep.LOGIN -> Column(Modifier.fillMaxSize().padding(padding)) {
                val connector = viewModel.connector
                if (connector != null) {
                    StoreLoginWebView(connector, onCaptured = viewModel::captured, modifier = Modifier.weight(1f))
                }
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = IncomeGreen, modifier = Modifier.size(16.dp))
                    Text(
                        "  This is ${store.company}'s own page. Your password goes only to ${store.company}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            ConnectStep.FINISHING, ConnectStep.DONE -> Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Text("Connecting ${store.label}…", modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

@Composable
private fun Benefit(title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = IncomeGreen, modifier = Modifier.size(22.dp))
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Fact(title: String, detail: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (title == "Good to know") Icon(Icons.Filled.WarningAmber, contentDescription = null, modifier = Modifier.size(16.dp).padding(end = 4.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The store's own login page. Nothing is read from the page itself: the app only looks at the
 * address the store sends the browser to at the end, or at the store's login cookie. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun StoreLoginWebView(connector: StoreConnector, onCaptured: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var progress by remember { mutableStateOf(0) }
    // Always call the latest callback without rebuilding the WebView — rebuilding it is what makes
    // the text cursor jump back to the start on every keystroke.
    val captured by rememberUpdatedState(onCaptured)

    fun checkCookies(agent: String?) {
        val url = connector.cookieUrl ?: return
        connector.captureFromCookies(CookieManager.getInstance().getCookie(url), agent ?: "")?.let(captured)
    }

    // One WebView for the whole login, created once and never recreated. This keeps the login
    // form's text input and taps working normally.
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            isFocusableInTouchMode = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    connector.captureFromUrl(url)?.let { captured(it); return true }
                    // Never hand the login over to another app.
                    return !(url.startsWith("https://") || url.startsWith("http://"))
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    url?.let { connector.captureFromUrl(it) }?.let(captured)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    checkCookies(view?.settings?.userAgentString)
                }
            }
            loadUrl(connector.loginUrl())
        }
    }

    // Some sites sign in without loading a new page (Nemlig sets a cookie): check now and then.
    LaunchedEffect(Unit) {
        if (connector.cookieUrl != null) {
            while (true) {
                delay(1_500)
                checkCookies(webView.settings.userAgentString)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    Box(modifier) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
        if (progress in 1..99) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
