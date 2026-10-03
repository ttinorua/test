package com.financetracker.app.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.ai.LoanDocumentReader
import com.financetracker.app.data.prefs.CurrencySettings
import com.financetracker.app.data.prefs.Loan
import com.financetracker.app.data.prefs.LoansAndGoals
import com.financetracker.app.data.prefs.SavingsGoal
import com.financetracker.app.util.Formatters
import kotlinx.coroutines.launch

/** Settings > General > My loans & goals: what the AI advisor knows about the user's loans and
 * savings goals. A loan can be typed in or read from its (Danish) document by the AI. */
@Composable
fun LoansAndGoalsSettings(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loans by LoansAndGoals.loans.collectAsState()
    val goals by LoansAndGoals.goals.collectAsState()
    val currency by CurrencySettings.currencyCode.collectAsState()
    var editingLoan by remember { mutableStateOf<Loan?>(null) }
    var editingGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var reading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val pickDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        reading = true
        error = null
        scope.launch {
            LoanDocumentReader.read(context, uris)
                .onSuccess { editingLoan = it }
                .onFailure { error = it.message ?: "Couldn't read the document." }
            reading = false
        }
    }

    Column(modifier = modifier) {
        Text("My loans & goals", style = MaterialTheme.typography.titleMedium)
        Text(
            "Your loans, mortgages and savings goals, so the AI advisor can give advice that fits them. " +
                "Read a loan document (PDF or photos of the pages, Danish is fine) and the AI fills in the terms " +
                "in English for you to check. Only the terms are kept — not the document or personal details.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp, top = 4.dp)
        )
        loans.forEach { loan ->
            ItemRow(
                title = loan.name.ifBlank { "Loan" },
                detail = listOfNotNull(
                    loan.remainingDebt?.let { "Debt ${Formatters.currency(it, currency)}" },
                    loan.interestRatePct?.let { "$it%" + (loan.contributionRatePct?.let { c -> " + $c%" } ?: "") },
                    loan.monthlyPayment?.let { "${Formatters.currency(it, currency)}/month" }
                ).joinToString(" · ").ifBlank { loan.lender },
                onClick = { editingLoan = loan }
            )
        }
        goals.forEach { goal ->
            ItemRow(
                title = goal.name.ifBlank { "Goal" },
                detail = listOfNotNull(
                    goal.savedAmount?.let { Formatters.currency(it, currency) },
                    goal.targetAmount?.let { "of ${Formatters.currency(it, currency)}" },
                    goal.targetDate.takeIf { it.isNotBlank() }?.let { "by $it" }
                ).joinToString(" "),
                onClick = { editingGoal = goal }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            OutlinedButton(onClick = { editingLoan = Loan() }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Loan", modifier = Modifier.padding(start = 4.dp))
            }
            OutlinedButton(onClick = { editingGoal = SavingsGoal() }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Goal", modifier = Modifier.padding(start = 4.dp))
            }
        }
        OutlinedButton(
            onClick = { pickDocument.launch(arrayOf("application/pdf", "image/*")) },
            enabled = !reading,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            if (reading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Reading the document…", modifier = Modifier.padding(start = 8.dp))
            } else {
                Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Read loan document", modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (!LoanDocumentReader.isAvailable) {
            Text(
                "Reading documents needs Gemini or Claude (AI assistant above).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }

    editingLoan?.let { loan ->
        LoanDialog(
            loan = loan,
            isNew = loans.none { it.id == loan.id },
            onSave = {
                LoansAndGoals.saveLoan(it)
                editingLoan = null
            },
            onDelete = {
                LoansAndGoals.deleteLoan(loan.id)
                editingLoan = null
            },
            onDismiss = { editingLoan = null }
        )
    }
    editingGoal?.let { goal ->
        GoalDialog(
            goal = goal,
            isNew = goals.none { it.id == goal.id },
            onSave = {
                LoansAndGoals.saveGoal(it)
                editingGoal = null
            },
            onDelete = {
                LoansAndGoals.deleteGoal(goal.id)
                editingGoal = null
            },
            onDismiss = { editingGoal = null }
        )
    }
}

@Composable
private fun ItemRow(title: String, detail: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (detail.isNotBlank()) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val LOAN_KINDS = listOf("mortgage" to "Mortgage", "bank_loan" to "Bank loan", "car_loan" to "Car loan", "student_loan" to "Student loan", "other" to "Other")

@Composable
private fun LoanDialog(loan: Loan, isNew: Boolean, onSave: (Loan) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var draft by remember(loan.id) { mutableStateOf(loan) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Loan" else "Edit loan") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isNew && loan.summary.isNotBlank()) {
                    Text(
                        "Read from your document — check the figures before saving.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Field("Name", draft.name) { draft = draft.copy(name = it) }
                Field("Lender", draft.lender) { draft = draft.copy(lender = it) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LOAN_KINDS.take(3).forEach { (key, label) ->
                        FilterChip(selected = draft.kind == key, onClick = { draft = draft.copy(kind = key) }, label = { Text(label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LOAN_KINDS.drop(3).forEach { (key, label) ->
                        FilterChip(selected = draft.kind == key, onClick = { draft = draft.copy(kind = key) }, label = { Text(label) })
                    }
                }
                Field("Loan type", draft.loanType) { draft = draft.copy(loanType = it) }
                NumberField("Remaining debt", draft.remainingDebt) { draft = draft.copy(remainingDebt = it) }
                NumberField("Original amount", draft.originalAmount) { draft = draft.copy(originalAmount = it) }
                NumberField("Interest rate (% a year)", draft.interestRatePct) { draft = draft.copy(interestRatePct = it) }
                NumberField("Contribution rate / bidragssats (%)", draft.contributionRatePct) { draft = draft.copy(contributionRatePct = it) }
                NumberField("Monthly payment", draft.monthlyPayment) { draft = draft.copy(monthlyPayment = it) }
                NumberField("Years left", draft.yearsLeft) { draft = draft.copy(yearsLeft = it) }
                Field("Rate type (fixed / variable)", draft.rateType) { draft = draft.copy(rateType = it) }
                Field("Next rate reset", draft.nextRateReset) { draft = draft.copy(nextRateReset = it) }
                Field("Interest-only until", draft.interestOnlyUntil) { draft = draft.copy(interestOnlyUntil = it) }
                Field("Ends", draft.endDate) { draft = draft.copy(endDate = it) }
                Field("Early repayment terms", draft.earlyRepayment, singleLine = false) { draft = draft.copy(earlyRepayment = it) }
                Field("Other terms", draft.otherTerms, singleLine = false) { draft = draft.copy(otherTerms = it) }
                if (draft.summary.isNotBlank()) {
                    Text("Document summary", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                    Text(draft.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete loan", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft.copy(name = draft.name.trim().ifBlank { "Loan" })) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun GoalDialog(goal: SavingsGoal, isNew: Boolean, onSave: (SavingsGoal) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var draft by remember(goal.id) { mutableStateOf(goal) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Savings goal" else "Edit goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Field("Name, e.g. Emergency fund", draft.name) { draft = draft.copy(name = it) }
                NumberField("Target amount", draft.targetAmount) { draft = draft.copy(targetAmount = it) }
                NumberField("Saved so far", draft.savedAmount) { draft = draft.copy(savedAmount = it) }
                NumberField("Saving per month", draft.monthlySaving) { draft = draft.copy(monthlySaving = it) }
                Field("Target date (YYYY-MM-DD)", draft.targetDate) { draft = draft.copy(targetDate = it) }
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete goal", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft.copy(name = draft.name.trim().ifBlank { "Goal" })) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun Field(label: String, value: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth()
    )
}

/** A number field that accepts both 1234.5 and Danish 1.234,5. */
@Composable
private fun NumberField(label: String, value: Double?, onChange: (Double?) -> Unit) {
    var text by remember { mutableStateOf(value?.let { formatNumber(it) }.orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(parseNumber(it))
        },
        label = { Text(label) },
        singleLine = true,
        isError = text.isNotBlank() && parseNumber(text) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun formatNumber(value: Double): String =
    if (value == Math.floor(value) && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString()

internal fun parseNumber(raw: String): Double? {
    val text = raw.trim().replace(" ", "")
    if (text.isEmpty()) return null
    val normalized = when {
        // 1.234.567,89 or 1234,5 (Danish)
        text.contains(',') && (text.lastIndexOf(',') > text.lastIndexOf('.')) -> text.replace(".", "").replace(',', '.')
        // 1,234,567.89
        text.contains(',') -> text.replace(",", "")
        // 1.234.567 (Danish thousands without decimals)
        text.count { it == '.' } > 1 -> text.replace(".", "")
        else -> text
    }
    return normalized.toDoubleOrNull()
}
