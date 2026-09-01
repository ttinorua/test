package com.example.personalfinance.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector

/** Maps a [com.example.personalfinance.data.Category.icon] key to the icon shown in the UI. */
object CategoryIcons {
    private val icons: Map<String, ImageVector> = mapOf(
        "salary" to Icons.Filled.AttachMoney,
        "gift" to Icons.Filled.CardGiftcard,
        "trending_up" to Icons.Filled.TrendingUp,
        "food" to Icons.Filled.Fastfood,
        "transport" to Icons.Filled.DirectionsCar,
        "shopping" to Icons.Filled.ShoppingBag,
        "bills" to Icons.Filled.Receipt,
        "entertainment" to Icons.Filled.LocalMovies,
        "health" to Icons.Filled.FavoriteBorder,
        "housing" to Icons.Filled.Home,
        "other" to Icons.Filled.MoreHoriz
    )

    val keys: List<String> = icons.keys.toList()

    fun forKey(key: String): ImageVector = icons[key] ?: Icons.Filled.MoreHoriz
}
