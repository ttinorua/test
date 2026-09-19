package com.financetracker.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.financetracker.app.util.SimilarTransactionsPrompt

/** Shown right after a manual category edit, when other transactions share the same note or
 * amount as the one just edited — lets the user apply the new category to all of them at once. */
@Composable
fun SimilarTransactionsPromptDialog(
    prompt: SimilarTransactionsPrompt,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val count = prompt.similar.size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update similar transactions?") },
        text = {
            Text(
                "$count other transaction${if (count == 1) "" else "s"} with the same name or " +
                    "amount ${if (count == 1) "isn't" else "aren't"} filed under " +
                    "\"${prompt.newCategoryName}\" yet. Update ${if (count == 1) "it" else "them"} too?"
            )
        },
        confirmButton = { TextButton(onClick = onApply) { Text("Update") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}
