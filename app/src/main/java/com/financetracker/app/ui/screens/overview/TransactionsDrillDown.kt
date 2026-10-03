package com.financetracker.app.ui.screens.overview

import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.util.CategoryFilter
import com.financetracker.app.util.GroupByOption
import com.financetracker.app.util.PeriodOption

/** What a tapped bar, row or KPI on the Spending and Trends screens drills into: the transactions
 * behind that number, opened in [GroupTransactionsScreen]. A null [groupBy] means "every
 * transaction" rather than one Account/Main category/Category bucket. */
data class TransactionsDrillDown(
    val title: String,
    val periodOption: PeriodOption,
    val customRange: Pair<Long, Long>?,
    val accountId: Long?,
    val groupBy: GroupByOption? = null,
    val key: String = "",
    val type: TransactionType? = null,
    val categoryFilter: CategoryFilter = CategoryFilter.All
) {
    fun route(): String =
        "group_transactions/${groupBy?.name ?: NONE}/${Uri.encode(key.ifEmpty { NONE })}/${periodOption.name}/" +
            "${customRange?.first ?: -1L}/${customRange?.second ?: -1L}/${accountId ?: -1L}/" +
            "${type?.name ?: NONE}/${Uri.encode(categoryFilter.encode())}/${Uri.encode(title)}"

    companion object {
        private const val NONE = "NONE"

        const val ROUTE =
            "group_transactions/{groupBy}/{key}/{periodOption}/{from}/{to}/{accountId}/{type}/{filter}/{title}"

        val arguments = listOf(
            navArgument("groupBy") { type = NavType.StringType },
            navArgument("key") { type = NavType.StringType },
            navArgument("periodOption") { type = NavType.StringType },
            navArgument("from") { type = NavType.LongType },
            navArgument("to") { type = NavType.LongType },
            navArgument("accountId") { type = NavType.LongType },
            navArgument("type") { type = NavType.StringType },
            navArgument("filter") { type = NavType.StringType },
            navArgument("title") { type = NavType.StringType }
        )

        fun from(args: Bundle): TransactionsDrillDown {
            val periodOption = PeriodOption.valueOf(args.getString("periodOption")!!)
            val from = args.getLong("from")
            val to = args.getLong("to")
            val key = Uri.decode(args.getString("key")!!)
            return TransactionsDrillDown(
                title = Uri.decode(args.getString("title")!!),
                periodOption = periodOption,
                customRange = if (periodOption == PeriodOption.CUSTOM && from >= 0 && to >= 0) from to to else null,
                accountId = args.getLong("accountId").takeIf { it >= 0 },
                groupBy = args.getString("groupBy")!!.takeIf { it != NONE }?.let { GroupByOption.valueOf(it) },
                key = if (key == NONE) "" else key,
                type = args.getString("type")!!.takeIf { it != NONE }?.let { TransactionType.valueOf(it) },
                categoryFilter = CategoryFilter.decode(Uri.decode(args.getString("filter")!!))
            )
        }
    }
}
