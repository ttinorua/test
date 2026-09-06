package com.financetracker.app.data.importexport

import com.financetracker.app.data.db.entity.TransactionWithDetails
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Writes the transaction list back out as CSV or XLSX, e.g. for backup or
 * moving data to a spreadsheet app. Column order matches [SpreadsheetParser]'s
 * expected import format, so an exported file can be re-imported as-is.
 */
object SpreadsheetExporter {

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun exportCsv(transactions: List<TransactionWithDetails>, output: OutputStream) {
        OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
            writer.append("Date,Description,MainCategory,Category,Account,Type,Amount\n")
            transactions.forEach { t ->
                writer.append(DATE_FORMAT.format(t.date)).append(',')
                writer.append(csvEscape(t.note)).append(',')
                writer.append(csvEscape(t.mainCategoryName ?: "Uncategorized")).append(',')
                writer.append(csvEscape(t.categoryName ?: "Uncategorized")).append(',')
                writer.append(csvEscape(t.accountName)).append(',')
                writer.append(t.type.name).append(',')
                writer.append(t.amount.toString()).append('\n')
            }
        }
    }

    fun exportXlsx(transactions: List<TransactionWithDetails>, output: OutputStream) {
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Transactions")
            val headerStyle = workbook.createCellStyle().apply {
                val font = workbook.createFont().apply { bold = true }
                setFont(font)
                fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
            }

            val headerRow = sheet.createRow(0)
            listOf("Date", "Description", "MainCategory", "Category", "Account", "Type", "Amount")
                .forEachIndexed { i, title ->
                    val cell = headerRow.createCell(i)
                    cell.setCellValue(title)
                    cell.cellStyle = headerStyle
                }

            transactions.forEachIndexed { rowIdx, t ->
                val row = sheet.createRow(rowIdx + 1)
                row.createCell(0).setCellValue(DATE_FORMAT.format(t.date))
                row.createCell(1).setCellValue(t.note)
                row.createCell(2).setCellValue(t.mainCategoryName ?: "Uncategorized")
                row.createCell(3).setCellValue(t.categoryName ?: "Uncategorized")
                row.createCell(4).setCellValue(t.accountName)
                row.createCell(5).setCellValue(t.type.name)
                row.createCell(6).setCellValue(t.amount)
            }

            // Fixed column widths (in 1/256 of a character) instead of autoSizeColumn:
            // POI's auto-sizing needs java.awt font metrics, which aren't available on Android.
            val widths = intArrayOf(12, 40, 20, 20, 20, 12, 14)
            widths.forEachIndexed { i, w -> sheet.setColumnWidth(i, w * 256) }

            workbook.write(output)
        }
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
