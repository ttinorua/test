package com.financetracker.app.util.advisor

import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.util.identityTokensOf
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The calculations behind the AI advisor's tools. The app works out every figure itself (exact,
 * from the user's own data) and the AI only interprets them — AIs are unreliable at arithmetic.
 * All functions are pure, so they're unit-tested directly.
 *
 * Transactions passed in are expected to be already filtered to what counts toward totals (the
 * caller applies the transfer/reporting-date rules the rest of the app uses).
 */
object FinanceAnalysis {

    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")
    private const val DAY = 24L * 60 * 60 * 1000

    fun monthStart(time: Long, offsetMonths: Int = 0): Long = Calendar.getInstance(UTC).apply {
        timeInMillis = time
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.MONTH, offsetMonths)
    }.timeInMillis

    data class Total(val key: String, val amount: Double, val count: Int)

    /** Totals of [type] transactions per key (biggest first). */
    fun totalsBy(
        transactions: List<TransactionWithDetails>,
        type: TransactionType,
        key: (TransactionWithDetails) -> String
    ): List<Total> = transactions.filter { it.type == type }
        .groupBy(key)
        .map { (k, txs) -> Total(k, txs.sumOf { it.amount }, txs.size) }
        .sortedByDescending { it.amount }

    /** A readable merchant name for grouping: the note without running references/codes. */
    fun merchantOf(note: String): String =
        identityTokensOf(note).joinToString(" ").ifBlank { note.trim().lowercase() }.take(40)

    data class MonthTotals(val monthStart: Long, val income: Double, val expense: Double) {
        val net: Double get() = income - expense
        val savingsRatePct: Double? get() = if (income > 0) (income - expense) / income * 100 else null
    }

    /** Income/expense for the last [months] calendar months, oldest first, ending with [now]'s month. */
    fun monthlyTotals(transactions: List<TransactionWithDetails>, months: Int, now: Long): List<MonthTotals> =
        (months - 1 downTo 0).map { back ->
            val from = monthStart(now, -back)
            val to = monthStart(now, -back + 1)
            val inMonth = transactions.filter { it.date in from until to }
            MonthTotals(
                from,
                inMonth.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                inMonth.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            )
        }

    enum class Cadence(val label: String, val perYear: Double) {
        WEEKLY("weekly", 52.0),
        MONTHLY("monthly", 12.0),
        QUARTERLY("quarterly", 4.0),
        YEARLY("yearly", 1.0)
    }

    data class RecurringPayment(
        val merchant: String,
        val cadence: Cadence,
        val lastAmount: Double,
        val lastDate: Long,
        /** The amount before the latest change, when the latest payment differs from the one before. */
        val previousAmount: Double?,
        val occurrences: Int,
        val category: String?
    ) {
        val yearlyCost: Double get() = lastAmount * cadence.perYear
        val priceChangePct: Double? get() = previousAmount?.takeIf { it > 0 }?.let { (lastAmount - it) / it * 100 }
    }

    /** Subscriptions and other repeating payments: the same merchant at a regular interval, still
     * active (last seen within ~1.5 intervals of [now]). */
    fun recurringPayments(transactions: List<TransactionWithDetails>, now: Long): List<RecurringPayment> {
        val expenses = transactions.filter { it.type == TransactionType.EXPENSE && it.note.isNotBlank() }
        return expenses.groupBy { merchantOf(it.note) }.mapNotNull { (merchant, txs) ->
            if (txs.size < 3) return@mapNotNull null
            val sorted = txs.sortedBy { it.date }
            val gaps = sorted.zipWithNext { a, b -> (b.date - a.date) / DAY }.filter { it > 0 }
            if (gaps.isEmpty()) return@mapNotNull null
            val median = gaps.sorted()[gaps.size / 2]
            val cadence = when (median) {
                in 5..9 -> Cadence.WEEKLY
                in 25..35 -> Cadence.MONTHLY
                in 80..100 -> Cadence.QUARTERLY
                in 350..380 -> Cadence.YEARLY
                else -> return@mapNotNull null
            }
            // Mostly regular: at least 2/3 of the gaps close to the cadence.
            val regular = gaps.count { abs(it - median) <= maxOf(4L, median / 6) }
            if (regular * 3 < gaps.size * 2) return@mapNotNull null
            val last = sorted.last()
            if (now - last.date > median * DAY * 3 / 2 + 5 * DAY) return@mapNotNull null
            val previous = sorted[sorted.size - 2].amount.takeIf { abs(it - last.amount) > 0.005 }
            RecurringPayment(
                merchant = merchant,
                cadence = cadence,
                lastAmount = last.amount,
                lastDate = last.date,
                previousAmount = previous,
                occurrences = sorted.size,
                category = last.categoryName
            )
        }.sortedByDescending { it.yearlyCost }
    }

    data class BudgetLine(val name: String, val budget: Double, val spent: Double, val elapsedFraction: Double) {
        val usedPct: Double get() = if (budget > 0) spent / budget * 100 else 0.0
        /** Spending at the current pace extrapolated to the whole month. */
        val projected: Double get() = if (elapsedFraction > 0) spent / elapsedFraction else spent
        val onTrackToExceed: Boolean get() = projected > budget * 1.02
    }

    /** How far through [now]'s month we are, 0..1 (day-based). */
    fun elapsedFractionOfMonth(now: Long): Double {
        val start = monthStart(now)
        val end = monthStart(now, 1)
        return ((now - start).toDouble() / (end - start)).coerceIn(0.03, 1.0)
    }
}

/** Loan and savings maths for the advisor, in plain annuity terms (how Danish mortgages and most
 * bank loans are paid: equal monthly payments, interest on the remaining debt). */
object FinanceCalculators {

    data class LoanResult(
        val monthlyPayment: Double,
        val months: Int,
        val totalInterest: Double,
        val totalPaid: Double
    )

    /**
     * An annuity loan of [principal] at [annualRatePct] (plus [contributionRatePct], the Danish
     * mortgage "bidragssats", charged on the remaining debt like interest) over [years].
     * With [extraMonthly] the same payment is kept plus the extra, and the loan ends sooner.
     */
    fun loan(
        principal: Double,
        annualRatePct: Double,
        years: Double,
        contributionRatePct: Double = 0.0,
        extraMonthly: Double = 0.0
    ): LoanResult {
        require(principal > 0 && years > 0) { "Principal and years must be positive." }
        val n = (years * 12).roundToInt().coerceAtLeast(1)
        val r = (annualRatePct + contributionRatePct) / 100 / 12
        val payment = if (r == 0.0) principal / n else principal * r / (1 - (1 + r).pow(-n))
        var balance = principal
        var interest = 0.0
        var months = 0
        while (balance > 0.005 && months < 1200) {
            val monthInterest = balance * r
            interest += monthInterest
            val pay = minOf(payment + extraMonthly, balance + monthInterest)
            balance = balance + monthInterest - pay
            months++
        }
        return LoanResult(payment, months, interest, principal + interest)
    }

    data class ExtraPaymentResult(val base: LoanResult, val withExtra: LoanResult) {
        val monthsSaved: Int get() = base.months - withExtra.months
        val interestSaved: Double get() = base.totalInterest - withExtra.totalInterest
    }

    fun extraPayment(
        principal: Double,
        annualRatePct: Double,
        years: Double,
        extraMonthly: Double,
        contributionRatePct: Double = 0.0
    ) = ExtraPaymentResult(
        loan(principal, annualRatePct, years, contributionRatePct),
        loan(principal, annualRatePct, years, contributionRatePct, extraMonthly)
    )

    /** Months until [current] grows to [target] saving [monthly] each month at [annualReturnPct];
     * null if it never gets there within 100 years. */
    fun monthsToGoal(target: Double, current: Double, monthly: Double, annualReturnPct: Double = 0.0): Int? {
        if (current >= target) return 0
        val r = annualReturnPct / 100 / 12
        var balance = current
        var months = 0
        while (balance < target) {
            if (months >= 1200) return null
            balance = balance * (1 + r) + monthly
            months++
        }
        return months
    }

    /** The monthly saving needed to grow [current] to [target] in [months] at [annualReturnPct]. */
    fun monthlyNeeded(target: Double, current: Double, months: Int, annualReturnPct: Double = 0.0): Double {
        require(months > 0) { "Months must be positive." }
        val r = annualReturnPct / 100 / 12
        val grown = current * (1 + r).pow(months)
        val remaining = (target - grown).coerceAtLeast(0.0)
        return if (r == 0.0) remaining / months else remaining * r / ((1 + r).pow(months) - 1)
    }
}
