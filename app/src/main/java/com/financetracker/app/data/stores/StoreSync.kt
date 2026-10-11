package com.financetracker.app.data.stores

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.financetracker.app.FinanceApp
import com.financetracker.app.data.ai.CategorySuggester
import com.financetracker.app.data.ai.ParsedReceipt
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.receipts.Receipts
import com.financetracker.app.data.repository.FinanceRepository
import com.financetracker.app.util.ReceiptMatcher
import com.financetracker.app.util.ReceiptSplits
import com.financetracker.app.util.ShoppingStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Fetches receipts from the connected store accounts: the last 12 months when an account is
 * connected, then what's new after each bank sync. */
object StoreSync {
    private const val DAY = 86_400_000L
    private const val FIRST_FETCH_DAYS = 365L
    /** Receipts can show up in a store account a little late, so each fetch looks back a bit. */
    private const val OVERLAP_DAYS = 14L

    private val _running = MutableStateFlow<Set<String>>(emptySet())
    /** Accounts being fetched right now. */
    val running: StateFlow<Set<String>> = _running

    /** One fetch at a time: two at once would both renew the same sign-in, and a store that allows
     * each renewal only once then rejects the second and signs the account out. */
    private val lock = Mutex()

    /** Fetches every connected account (or just [accountId]); returns how many receipts were new. */
    suspend fun syncAll(context: Context, repository: FinanceRepository, accountId: String? = null): Int = lock.withLock {
        var added = 0
        StoreAccounts.load(context).filter { accountId == null || it.id == accountId }.forEach { account ->
            added += sync(context, repository, account)
        }
        added
    }

    private suspend fun sync(context: Context, repository: FinanceRepository, account: StoreAccount): Int {
        val connector = StoreConnector.forStore(account.store) ?: return 0
        if (account.secret.isEmpty()) return 0
        _running.value = _running.value + account.id
        try {
            val now = System.currentTimeMillis()
            val since = if (account.lastSyncAt == 0L) now - FIRST_FETCH_DAYS * DAY else maxOf(account.lastSyncAt - OVERLAP_DAYS * DAY, now - FIRST_FETCH_DAYS * DAY)
            val (fetched, secret) = connector.fetch(account.secret, since)
            val fresh = fetched.filter { it.receipt.total > 0 && repository.findReceipt(account.store.id, it.receipt.externalId) == null }
            val saved = save(repository, account.store.id, fresh)
            StoreAccounts.update(context, account.id) {
                it.copy(
                    secret = secret,
                    lastSyncAt = now,
                    receiptCount = it.receiptCount + saved,
                    lastReceiptAt = maxOf(it.lastReceiptAt, fetched.maxOfOrNull { r -> r.receipt.purchasedAt } ?: 0L),
                    problem = null
                )
            }
            return saved
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val renewed = (e as? StoreFetchFailed)?.secret
            val failure = (e as? StoreFetchFailed)?.cause ?: e
            val problem = if (failure is StoreLoginExpired) "Sign in again" else "Couldn't fetch receipts (${failure.message ?: "no connection"})"
            StoreAccounts.update(context, account.id) { it.copy(secret = renewed ?: it.secret, problem = problem) }
        } finally {
            _running.value = _running.value - account.id
        }
        return 0
    }

    /** Sorts the items into categories, matches each receipt to its payment and saves it, split. */
    private suspend fun save(repository: FinanceRepository, source: String, receipts: List<StoreReceipt>): Int {
        if (receipts.isEmpty()) return 0
        val categorised = ItemCategories.assign(repository, receipts)
        val all = repository.observeTransactions().first()
        val transactions = all.filter { !ReceiptSplits.isPart(it.id) }
        val taken = repository.observeTransactionIdsWithReceipt().first().toMutableSet()
        var count = 0
        for (receipt in categorised.sortedBy { it.purchasedAt }) {
            val match = ReceiptMatcher.best(receipt.total, receipt.purchasedAt, receipt.storeName, transactions, taken)
            match?.let { taken += it.id }
            Receipts.save(repository, receipt, source, match?.id, split = true)
            count++
        }
        return count
    }

    /** Starts a fetch in the background (it keeps going if the screen is closed). */
    fun enqueue(context: Context, accountId: String? = null) {
        val request = OneTimeWorkRequestBuilder<StoreSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(StoreSyncWorker.ACCOUNT to accountId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "store_sync" + (accountId?.let { "_$it" } ?: ""),
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}

class StoreSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as FinanceApp).repository
        StoreSync.syncAll(applicationContext, repository, inputData.getString(ACCOUNT))
        return Result.success()
    }

    companion object {
        const val ACCOUNT = "account"
    }
}

/** Puts each fetched item in one of the user's expense categories: the category the same product
 * had on an earlier receipt (so moving an item once teaches it), else the AI's suggestion, else
 * none (it then counts in the payment's own category). */
internal object ItemCategories {
    suspend fun assign(repository: FinanceRepository, receipts: List<StoreReceipt>): List<ParsedReceipt> {
        val categories = repository.observeCategories().first().filter { it.type == TransactionType.EXPENSE }
        val known = repository.getAllReceiptItems().filter { it.categoryId != null }
            .sortedBy { it.id }
            .associate { ShoppingStats.key(it.name) to it.categoryId!! }
            .toMutableMap()
        val unknown = linkedMapOf<String, String>()
        receipts.forEach { r ->
            r.receipt.items.forEachIndexed { i, item ->
                val key = ShoppingStats.key(item.name)
                if (key !in known && key !in unknown) {
                    val hint = r.hints.getOrNull(i)
                    unknown[key] = "Supermarket item: ${item.name}" + (hint?.let { " ($it)" } ?: "")
                }
            }
        }
        if (unknown.isNotEmpty() && categories.isNotEmpty()) {
            unknown.entries.chunked(CategorySuggester.BATCH_SIZE).forEach { chunk ->
                val result = runCatching { CategorySuggester.suggestBatch(chunk.map { it.value }, categories).getOrNull() }.getOrNull()
                    ?: return@forEach
                chunk.forEachIndexed { i, (key, _) -> result.getOrNull(i)?.let { known[key] = it.id } }
            }
        }
        return receipts.map { r ->
            r.receipt.copy(items = r.receipt.items.map { it.copy(categoryId = known[ShoppingStats.key(it.name)]) })
        }
    }
}
