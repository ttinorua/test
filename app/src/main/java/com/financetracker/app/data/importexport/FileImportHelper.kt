package com.financetracker.app.data.importexport

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** Bridges Android's SAF file picker (a content [Uri]) to the pure-Kotlin [SpreadsheetParser]. */
object FileImportHelper {

    fun parse(context: Context, uri: Uri): ImportResult {
        val displayName = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: ""
        val mimeType = context.contentResolver.getType(uri) ?: ""
        val isCsv = displayName.endsWith(".csv", ignoreCase = true) ||
            displayName.endsWith(".txt", ignoreCase = true) ||
            mimeType.contains("csv", ignoreCase = true)

        val stream = context.contentResolver.openInputStream(uri)
            ?: return ImportResult(emptyList(), listOf("Could not open the selected file."))

        return stream.use { input ->
            if (isCsv) {
                SpreadsheetParser.parseCsv(input)
            } else {
                SpreadsheetParser.parseWorkbook(input)
            }
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        ) ?: return null
        return cursor.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) it.getString(idx) else null
            } else {
                null
            }
        }
    }
}
