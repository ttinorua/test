package com.financetracker.app.data.importexport

import com.financetracker.app.data.db.entity.TransactionType
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class ParsedTransactionRow(
    val rowNumber: Int,
    val date: Long,
    val note: String,
    val categoryName: String,
    val type: TransactionType,
    val amount: Double
)

data class ImportResult(
    val rows: List<ParsedTransactionRow>,
    val errors: List<String>
)

/**
 * Parses bank/expense export files (.csv, .xlsx, .xls) into transaction rows.
 * Column names are matched against common aliases so real-world exports from
 * banks and spreadsheet apps work without a fixed template.
 */
object SpreadsheetParser {

    private val DATE_ALIASES = listOf("date", "transaction date", "posted date", "trans date", "txn date")
    private val DESC_ALIASES =
        listOf("description", "note", "notes", "memo", "details", "payee", "merchant", "narrative")
    private val CATEGORY_ALIASES = listOf("category", "categories")
    private val TYPE_ALIASES = listOf("type", "transaction type", "direction")
    private val AMOUNT_ALIASES = listOf("amount", "value", "transaction amount")
    private val DEBIT_ALIASES = listOf("debit", "withdrawal", "money out", "expense")
    private val CREDIT_ALIASES = listOf("credit", "deposit", "money in", "income")

    private val DATE_PATTERNS = listOf(
        "yyyy-MM-dd",
        "yyyy/MM/dd",
        "MM/dd/yyyy",
        "M/d/yyyy",
        "dd/MM/yyyy",
        "d/M/yyyy",
        "dd-MM-yyyy",
        "MM-dd-yyyy",
        "MMM d, yyyy",
        "MMM dd yyyy",
        "d MMM yyyy",
        "yyyyMMdd"
    )

    fun parseCsv(input: InputStream): ImportResult {
        val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
        val lines = reader.readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return ImportResult(emptyList(), listOf("The file is empty."))

        val rawRows = lines.map { splitCsvLine(it) }
        return parseTable(rawRows)
    }

    fun parseWorkbook(input: InputStream): ImportResult {
        return try {
            WorkbookFactory.create(input).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                val rawRows = mutableListOf<List<String>>()
                for (row in sheet) {
                    rawRows.add(rowToStrings(row))
                }
                parseTable(rawRows)
            }
        } catch (e: Exception) {
            ImportResult(emptyList(), listOf("Could not read spreadsheet: ${e.message ?: e.javaClass.simpleName}"))
        }
    }

    private fun rowToStrings(row: Row): List<String> {
        val lastCol = row.lastCellNum.toInt()
        if (lastCol < 0) return emptyList()
        return (0 until lastCol).map { idx ->
            val cell = row.getCell(idx) ?: return@map ""
            cellToString(cell)
        }
    }

    private fun cellToString(cell: Cell): String {
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue.trim()
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    fmt.timeZone = TimeZone.getTimeZone("UTC")
                    fmt.format(cell.dateCellValue)
                } else {
                    val d = cell.numericCellValue
                    if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> {
                try {
                    when (cell.cachedFormulaResultType) {
                        CellType.STRING -> cell.stringCellValue.trim()
                        CellType.NUMERIC -> cell.numericCellValue.toString()
                        CellType.BOOLEAN -> cell.booleanCellValue.toString()
                        else -> ""
                    }
                } catch (e: Exception) {
                    ""
                }
            }
            else -> ""
        }
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    result.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        result.add(sb.toString().trim())
        return result
    }

    private fun normalizeHeader(h: String): String =
        h.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")

    private fun findHeaderRowIndex(rawRows: List<List<String>>): Int {
        val allAliases = DATE_ALIASES + DESC_ALIASES + CATEGORY_ALIASES + TYPE_ALIASES +
            AMOUNT_ALIASES + DEBIT_ALIASES + CREDIT_ALIASES
        for (i in rawRows.indices.take(20)) {
            val normalized = rawRows[i].map { normalizeHeader(it) }
            val matches = normalized.count { it in allAliases }
            if (matches >= 2) return i
        }
        return -1
    }

    private fun parseTable(rawRows: List<List<String>>): ImportResult {
        if (rawRows.isEmpty()) return ImportResult(emptyList(), listOf("The file has no rows."))

        val headerIndex = findHeaderRowIndex(rawRows)
        if (headerIndex == -1) {
            return ImportResult(
                emptyList(),
                listOf(
                    "Could not find a header row. Expected columns like Date, Description, " +
                        "Category, Amount (or Debit/Credit)."
                )
            )
        }

        val headers = rawRows[headerIndex].map { normalizeHeader(it) }
        fun colIndex(aliases: List<String>): Int = headers.indexOfFirst { it in aliases }

        val dateCol = colIndex(DATE_ALIASES)
        val descCol = colIndex(DESC_ALIASES)
        val categoryCol = colIndex(CATEGORY_ALIASES)
        val typeCol = colIndex(TYPE_ALIASES)
        val amountCol = colIndex(AMOUNT_ALIASES)
        val debitCol = colIndex(DEBIT_ALIASES)
        val creditCol = colIndex(CREDIT_ALIASES)

        if (dateCol == -1) {
            return ImportResult(emptyList(), listOf("Could not find a Date column."))
        }
        if (amountCol == -1 && debitCol == -1 && creditCol == -1) {
            return ImportResult(
                emptyList(),
                listOf("Could not find an Amount column (or Debit/Credit columns).")
            )
        }

        val rows = mutableListOf<ParsedTransactionRow>()
        val errors = mutableListOf<String>()

        for (r in (headerIndex + 1) until rawRows.size) {
            val cells = rawRows[r]
            if (cells.all { it.isBlank() }) continue
            val displayRowNumber = r + 1

            fun cell(idx: Int): String = if (idx in cells.indices) cells[idx] else ""

            val dateStr = cell(dateCol)
            val date = parseDate(dateStr)
            if (date == null) {
                errors.add("Row $displayRowNumber: could not parse date \"$dateStr\", skipped.")
                continue
            }

            val type: TransactionType
            val amount: Double

            if (debitCol != -1 || creditCol != -1) {
                val debit = parseAmount(cell(debitCol))
                val credit = parseAmount(cell(creditCol))
                when {
                    debit != null && debit != 0.0 -> {
                        type = TransactionType.EXPENSE
                        amount = kotlin.math.abs(debit)
                    }
                    credit != null && credit != 0.0 -> {
                        type = TransactionType.INCOME
                        amount = kotlin.math.abs(credit)
                    }
                    else -> {
                        errors.add("Row $displayRowNumber: no debit/credit amount found, skipped.")
                        continue
                    }
                }
            } else {
                val rawAmount = parseAmount(cell(amountCol))
                if (rawAmount == null) {
                    errors.add("Row $displayRowNumber: could not parse amount \"${cell(amountCol)}\", skipped.")
                    continue
                }
                val typeText = cell(typeCol).lowercase(Locale.US)
                type = when {
                    typeText.contains("income") || typeText.contains("credit") || typeText.contains("deposit") ->
                        TransactionType.INCOME
                    typeText.contains("expense") || typeText.contains("debit") || typeText.contains("withdrawal") ->
                        TransactionType.EXPENSE
                    rawAmount < 0 -> TransactionType.EXPENSE
                    else -> TransactionType.INCOME
                }
                amount = kotlin.math.abs(rawAmount)
            }

            if (amount == 0.0) {
                errors.add("Row $displayRowNumber: amount is zero, skipped.")
                continue
            }

            val note = cell(descCol)
            val category = cell(categoryCol).ifBlank { "Uncategorized" }

            rows.add(
                ParsedTransactionRow(
                    rowNumber = displayRowNumber,
                    date = date,
                    note = note,
                    categoryName = category,
                    type = type,
                    amount = amount
                )
            )
        }

        return ImportResult(rows, errors)
    }

    private fun parseAmount(raw: String): Double? {
        if (raw.isBlank()) return null
        var s = raw.trim()
        var negative = false
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true
            s = s.substring(1, s.length - 1)
        }
        if (s.startsWith("-")) negative = true
        s = s.replace(Regex("[^0-9.,]"), "")
        if (s.isBlank()) return null
        if (s.contains(',') && s.contains('.')) {
            s = s.replace(",", "")
        } else if (s.contains(',') && !s.contains('.')) {
            val parts = s.split(',')
            s = if (parts.last().length == 2) s.replace(',', '.') else s.replace(",", "")
        }
        val value = s.toDoubleOrNull() ?: return null
        return if (negative) -value else value
    }

    private fun parseDate(raw: String): Long? {
        if (raw.isBlank()) return null
        val trimmed = raw.trim()

        trimmed.toDoubleOrNull()?.let { serial ->
            if (serial > 20000 && serial < 90000) {
                val date = DateUtil.getJavaDate(serial, false, TimeZone.getTimeZone("UTC"))
                return toUtcMidnight(date.time)
            }
        }

        for (pattern in DATE_PATTERNS) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US)
                fmt.isLenient = false
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                val parsed = fmt.parse(trimmed) ?: continue
                return toUtcMidnight(parsed.time)
            } catch (e: Exception) {
                // try next pattern
            }
        }
        return null
    }

    private fun toUtcMidnight(epochMillis: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = epochMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
