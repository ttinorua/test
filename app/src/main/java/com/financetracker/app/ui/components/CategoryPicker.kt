package com.financetracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.util.CategoryFilter

private val ACCORDION_CORNER = 12.dp

/** Rounds only the outer corners of an accordion: the first header's top, the last visible row's
 * bottom — so the whole list reads as one card. */
fun accordionShape(top: Boolean, bottom: Boolean): Shape = RoundedCornerShape(
    topStart = if (top) ACCORDION_CORNER else 0.dp,
    topEnd = if (top) ACCORDION_CORNER else 0.dp,
    bottomStart = if (bottom) ACCORDION_CORNER else 0.dp,
    bottomEnd = if (bottom) ACCORDION_CORNER else 0.dp
)

/** A main-category header. Collapsed it's filled with the app's accent container color; expanded
 * it blends into the card color of its subcategories below. */
@Composable
fun CategoryGroupHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    shape: Shape,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (expanded) CardDefaults.cardColors().containerColor else MaterialTheme.colorScheme.primaryContainer
    val content = if (expanded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .clickable(onClickLabel = if (expanded) "Collapse" else "Expand", onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.Remove else Icons.Filled.AddCircle,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 8.dp)
        )
        Box(
            modifier = Modifier
                .size(26.dp)
                .border(1.5.dp, content, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("$count", style = MaterialTheme.typography.labelMedium, color = content)
        }
    }
}

/**
 * A full-screen category picker: a search field on top and the categories grouped under their
 * main category as an accordion below. Being full screen with the search at the top, the list
 * always has room above the keyboard.
 *
 * [allowAll] adds an "All categories" choice and [allowMainSelection] an "All of <main>" choice in
 * each group — for filters; a transaction's own category is always a single category or
 * Uncategorized.
 */
@Composable
fun CategoryPickerDialog(
    title: String,
    categories: List<Category>,
    selected: CategoryFilter?,
    onDismiss: () -> Unit,
    onSelect: (CategoryFilter) -> Unit,
    allowAll: Boolean = false,
    allowMainSelection: Boolean = false,
    allowUncategorized: Boolean = true
) {
    var query by remember { mutableStateOf("") }
    val initiallyOpen = when (selected) {
        is CategoryFilter.Main -> selected.name
        is CategoryFilter.Single -> categories.firstOrNull { it.id == selected.categoryId }?.mainCategory
        else -> null
    }
    var expandedMains by remember { mutableStateOf(setOfNotNull(initiallyOpen)) }
    val trimmed = query.trim()
    val searching = trimmed.isNotEmpty()

    fun matches(text: String) = text.contains(trimmed, ignoreCase = true)

    // A main category matching the search shows all of its subcategories; otherwise only the
    // subcategories that match themselves.
    val groups = categories
        .groupBy { it.mainCategory }
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)
        .mapNotNull { (main, subs) ->
            val sorted = subs.sortedBy { it.name.lowercase() }
            val shown = if (!searching || matches(main)) sorted else sorted.filter { matches(it.name) }
            if (shown.isEmpty()) null else Triple(main, subs.size, shown)
        }
    val showAll = allowAll && !searching
    val showUncategorized = allowUncategorized && (!searching || matches("Uncategorized"))

    fun pick(filter: CategoryFilter) {
        onSelect(filter)
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search categories") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)
                ) {
                    val standalone = listOfNotNull(
                        if (showAll) CategoryFilter.All else null,
                        if (showUncategorized) CategoryFilter.Uncategorized else null
                    )
                    if (standalone.isNotEmpty()) {
                        itemsIndexed(standalone, key = { _, filter -> filter.encode() }) { index, filter ->
                            PickerRow(
                                text = filter.label,
                                colorHex = null,
                                selected = selected == filter,
                                emphasized = true,
                                showDivider = index < standalone.lastIndex,
                                shape = accordionShape(top = index == 0, bottom = index == standalone.lastIndex),
                                onClick = { pick(filter) }
                            )
                        }
                        item(key = "standalone_gap") { Spacer(modifier = Modifier.height(12.dp)) }
                    }
                    if (groups.isEmpty() && standalone.isEmpty()) {
                        item(key = "no_matches") {
                            Text(
                                "No matching categories",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    groups.forEachIndexed { groupIndex, (main, total, shown) ->
                        val isExpanded = searching || main in expandedMains
                        val isLastGroup = groupIndex == groups.lastIndex
                        item(key = "header_$main") {
                            CategoryGroupHeader(
                                title = main,
                                count = total,
                                expanded = isExpanded,
                                shape = accordionShape(top = groupIndex == 0, bottom = isLastGroup && !isExpanded),
                                onToggle = {
                                    expandedMains = if (main in expandedMains) expandedMains - main else expandedMains + main
                                },
                                modifier = Modifier.padding(top = if (groupIndex == 0) 0.dp else 2.dp)
                            )
                        }
                        if (isExpanded) {
                            val rows: List<CategoryFilter> =
                                (if (allowMainSelection) listOf(CategoryFilter.Main(main)) else emptyList()) +
                                    shown.map { CategoryFilter.Single(it.id, it.name) }
                            val colorById = shown.associate { it.id to it.colorHex }
                            itemsIndexed(rows, key = { _, row -> "row_${row.encode()}" }) { index, row ->
                                val isLastRow = index == rows.lastIndex
                                PickerRow(
                                    text = if (row is CategoryFilter.Main) "All of $main" else row.label,
                                    colorHex = (row as? CategoryFilter.Single)?.let { colorById[it.categoryId] },
                                    selected = selected == row ||
                                        (row is CategoryFilter.Single && selected is CategoryFilter.Single &&
                                            selected.categoryId == row.categoryId),
                                    emphasized = row is CategoryFilter.Main,
                                    showDivider = !isLastRow,
                                    shape = accordionShape(top = false, bottom = isLastGroup && isLastRow),
                                    onClick = { pick(row) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    text: String,
    colorHex: String?,
    selected: Boolean,
    emphasized: Boolean,
    showDivider: Boolean,
    shape: Shape,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardDefaults.cardColors().containerColor)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (colorHex != null) {
                CategoryColorDot(colorHex, modifier = Modifier.size(10.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (emphasized || selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (colorHex != null) 12.dp else 0.dp, end = 8.dp)
            )
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

/** A form field showing a transaction's category; tapping it opens [CategoryPickerDialog]. */
@Composable
fun CategoryPickerField(
    categories: List<Category>,
    selectedId: Long?,
    onSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Category"
) {
    var open by remember { mutableStateOf(false) }
    val selected = categories.firstOrNull { it.id == selectedId }
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected?.let { "${it.mainCategory} • ${it.name}" } ?: "Uncategorized",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = selected?.let { { CategoryColorDot(it.colorHex, modifier = Modifier.size(12.dp)) } },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        // The read-only text field would otherwise swallow the tap.
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClickLabel = "Choose category") { open = true }
        )
    }
    if (open) {
        CategoryPickerDialog(
            title = label,
            categories = categories,
            selected = selected?.let { CategoryFilter.Single(it.id, it.name) } ?: CategoryFilter.Uncategorized,
            onDismiss = { open = false },
            onSelect = { filter -> onSelected((filter as? CategoryFilter.Single)?.categoryId) }
        )
    }
}

/** A chip that narrows a screen to one category or main category (or all of them). */
@Composable
fun CategoryFilterChip(
    categories: List<Category>,
    selected: CategoryFilter,
    onSelected: (CategoryFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    AssistChip(
        onClick = { open = true },
        label = { Text(selected.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Category, contentDescription = null) },
        modifier = modifier
    )
    if (open) {
        CategoryPickerDialog(
            title = "Show category",
            categories = categories,
            selected = selected,
            onDismiss = { open = false },
            onSelect = onSelected,
            allowAll = true,
            allowMainSelection = true
        )
    }
}
