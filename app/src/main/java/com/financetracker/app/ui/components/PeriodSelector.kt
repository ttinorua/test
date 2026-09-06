package com.financetracker.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.financetracker.app.util.Formatters
import com.financetracker.app.util.PeriodOption
import com.financetracker.app.util.todayUtcMidnight

private const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSelectorChip(
    option: PeriodOption,
    customRange: Pair<Long, Long>?,
    onOptionSelected: (PeriodOption) -> Unit,
    onCustomRangeSelected: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showCustomPicker by remember { mutableStateOf(false) }

    val label = if (option == PeriodOption.CUSTOM && customRange != null) {
        "${Formatters.date(customRange.first)} – ${Formatters.date(customRange.second - ONE_DAY_MILLIS)}"
    } else {
        option.label
    }

    Box(modifier = modifier) {
        AssistChip(
            onClick = { menuExpanded = true },
            label = { Text(label) },
            leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) }
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            PeriodOption.entries.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.label) },
                    onClick = {
                        menuExpanded = false
                        if (opt == PeriodOption.CUSTOM) {
                            showCustomPicker = true
                        } else {
                            onOptionSelected(opt)
                        }
                    }
                )
            }
        }
    }

    if (showCustomPicker) {
        CustomRangePickerDialog(
            initialStart = customRange?.first ?: todayUtcMidnight(),
            initialEnd = customRange?.second?.minus(ONE_DAY_MILLIS) ?: todayUtcMidnight(),
            onDismiss = { showCustomPicker = false },
            onConfirm = { start, endExclusive ->
                onCustomRangeSelected(start, endExclusive)
                showCustomPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangePickerDialog(
    initialStart: Long,
    initialEnd: Long,
    onDismiss: () -> Unit,
    onConfirm: (start: Long, endExclusive: Long) -> Unit
) {
    var start by remember { mutableStateOf(initialStart) }
    var end by remember { mutableStateOf(initialEnd) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom range") },
        text = {
            Column {
                OutlinedTextField(
                    value = Formatters.date(start),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("From") },
                    trailingIcon = { TextButton(onClick = { pickingStart = true }) { Text("Change") } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = Formatters.date(end),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("To") },
                    trailingIcon = { TextButton(onClick = { pickingEnd = true }) { Text("Change") } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val lo = minOf(start, end)
                val hi = maxOf(start, end)
                onConfirm(lo, hi + ONE_DAY_MILLIS)
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (pickingStart) {
        val state = rememberDatePickerState(initialSelectedDateMillis = start)
        DatePickerDialog(
            onDismissRequest = { pickingStart = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { start = it }
                    pickingStart = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingStart = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state)
        }
    }

    if (pickingEnd) {
        val state = rememberDatePickerState(initialSelectedDateMillis = end)
        DatePickerDialog(
            onDismissRequest = { pickingEnd = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { end = it }
                    pickingEnd = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingEnd = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state)
        }
    }
}
