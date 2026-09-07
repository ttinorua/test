package com.financetracker.app

import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.importexport.SpreadsheetParser
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SpreadsheetParserTest {

    @Test
    fun `parses csv with single signed amount column`() {
        val csv = """
            Date,Description,Category,Amount
            2026-01-05,Grocery Store,Groceries,-54.30
            2026-01-07,Paycheck,Salary,2000
        """.trimIndent()

        val result = SpreadsheetParser.parseCsv(csv.byteInputStream())

        assertTrue(result.errors.isEmpty())
        assertEquals(2, result.rows.size)

        val expense = result.rows[0]
        assertEquals(TransactionType.EXPENSE, expense.type)
        assertEquals(54.30, expense.amount, 0.001)
        assertEquals("Groceries", expense.categoryName)

        val income = result.rows[1]
        assertEquals(TransactionType.INCOME, income.type)
        assertEquals(2000.0, income.amount, 0.001)
    }

    @Test
    fun `parses csv with debit and credit columns`() {
        val csv = """
            Date,Description,Debit,Credit
            2026-02-01,Rent,1200,
            2026-02-03,Refund,,150.00
        """.trimIndent()

        val result = SpreadsheetParser.parseCsv(csv.byteInputStream())

        assertTrue(result.errors.isEmpty())
        assertEquals(2, result.rows.size)
        assertEquals(TransactionType.EXPENSE, result.rows[0].type)
        assertEquals(1200.0, result.rows[0].amount, 0.001)
        assertEquals(TransactionType.INCOME, result.rows[1].type)
        assertEquals(150.0, result.rows[1].amount, 0.001)
    }

    @Test
    fun `handles quoted commas and skips unparseable rows without throwing`() {
        val csv = """
            Date,Description,Category,Amount
            2026-03-01,"Coffee, tea, and snacks",Dining,-12.50
            not-a-date,Bad row,Misc,10
        """.trimIndent()

        val result = SpreadsheetParser.parseCsv(csv.byteInputStream())

        assertEquals(1, result.rows.size)
        assertEquals("Coffee, tea, and snacks", result.rows[0].note)
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `parses xlsx workbook with type column and excel date`() {
        val bytes = ByteArrayOutputStream().use { bos ->
            XSSFWorkbook().use { wb ->
                val sheet = wb.createSheet("Sheet1")
                val header = sheet.createRow(0)
                listOf("Date", "Description", "Category", "Type", "Amount").forEachIndexed { i, v ->
                    header.createCell(i).setCellValue(v)
                }
                val dateStyle = wb.createCellStyle().apply {
                    dataFormat = wb.creationHelper.createDataFormat().getFormat("yyyy-mm-dd")
                }

                val row1 = sheet.createRow(1)
                val dateCell = row1.createCell(0)
                dateCell.setCellValue(java.util.Date(2026 - 1900, 3, 15))
                dateCell.cellStyle = dateStyle
                row1.createCell(1).setCellValue("Freelance payment")
                row1.createCell(2).setCellValue("Other Income")
                row1.createCell(3).setCellValue("Income")
                row1.createCell(4).setCellValue(500.0)

                wb.write(bos)
            }
            bos.toByteArray()
        }

        val result = SpreadsheetParser.parseWorkbook(ByteArrayInputStream(bytes))

        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(1, result.rows.size)
        assertEquals(TransactionType.INCOME, result.rows[0].type)
        assertEquals(500.0, result.rows[0].amount, 0.001)
        assertEquals("Freelance payment", result.rows[0].note)
    }

    @Test
    fun `parses xlsx date cell as the correct calendar day regardless of device timezone`() {
        val originalTimeZone = java.util.TimeZone.getDefault()
        try {
            // Denmark sits at UTC+1/+2 — ahead of UTC. If the parser naively reads
            // cell.dateCellValue (built in the *device's* timezone) and formats it as
            // UTC, local midnight on Sept 1 lands on Aug 31 in UTC. This reproduces
            // exactly that device setting to guard against it.
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Copenhagen"))

            val bytes = ByteArrayOutputStream().use { bos ->
                XSSFWorkbook().use { wb ->
                    val sheet = wb.createSheet("Sheet1")
                    val header = sheet.createRow(0)
                    listOf("Date", "Text", "Amount", "MainCategory", "Category").forEachIndexed { i, v ->
                        header.createCell(i).setCellValue(v)
                    }
                    val dateStyle = wb.createCellStyle().apply {
                        dataFormat = wb.creationHelper.createDataFormat().getFormat("yyyy-mm-dd")
                    }
                    val row1 = sheet.createRow(1)
                    val dateCell = row1.createCell(0)
                    dateCell.setCellValue(java.time.LocalDate.of(2026, 9, 1))
                    dateCell.cellStyle = dateStyle
                    row1.createCell(1).setCellValue("BS SOLRØD KOMMUNE")
                    row1.createCell(2).setCellValue(-3055.0)
                    row1.createCell(3).setCellValue("Education and institution")
                    row1.createCell(4).setCellValue("Education and institution (Other)")

                    wb.write(bos)
                }
                bos.toByteArray()
            }

            val result = SpreadsheetParser.parseWorkbook(ByteArrayInputStream(bytes))

            assertTrue(result.errors.toString(), result.errors.isEmpty())
            assertEquals(1, result.rows.size)

            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            cal.timeInMillis = result.rows[0].date
            assertEquals(2026, cal.get(java.util.Calendar.YEAR))
            assertEquals(java.util.Calendar.SEPTEMBER, cal.get(java.util.Calendar.MONTH))
            assertEquals(1, cal.get(java.util.Calendar.DAY_OF_MONTH))
        } finally {
            java.util.TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun `parses bank export with Text, MainCategory and Category columns`() {
        // Mirrors the real Danske-Bank-style export: Date, Text, Amount, Balance, Reconciled,
        // AccountNumber, AccountName, MainCategory, Category, Comment — minus sign for expenses.
        val csv = """
            Date,Text,Amount,Balance,Reconciled,AccountNumber,AccountName,MainCategory,Category,Comment
            2026-09-04,MCD SpotifySE,-199,34563.81,,6820 1609846,Privatkonto,Media,"Phone, internet, streaming and TV",
            2026-09-03,MobilePay REMA 1000 Solrød Str,-172.23,34762.81,,6820 1609846,Privatkonto,Food,Groceries,
            2026-09-01,BS SOLRØD KOMMUNE,-3055,36462.21,,6820 1609846,Privatkonto,Education and institution,Education and institution (Other),
            2026-08-31,Salary,26017.79,10000,,6820 1609846,Privatkonto,Income,Pay,
        """.trimIndent()

        val result = SpreadsheetParser.parseCsv(csv.byteInputStream())

        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(4, result.rows.size)

        val spotify = result.rows[0]
        assertEquals(TransactionType.EXPENSE, spotify.type)
        assertEquals(199.0, spotify.amount, 0.001)
        assertEquals("Media", spotify.mainCategoryName)
        assertEquals("Phone, internet, streaming and TV", spotify.categoryName)
        assertEquals("MCD SpotifySE", spotify.note)

        val salary = result.rows[3]
        assertEquals(TransactionType.INCOME, salary.type)
        assertEquals(26017.79, salary.amount, 0.001)
        assertEquals("Income", salary.mainCategoryName)
        assertEquals("Pay", salary.categoryName)
    }

    @Test
    fun `reports error when no recognizable header is found`() {
        val csv = """
            foo,bar,baz
            1,2,3
        """.trimIndent()

        val result = SpreadsheetParser.parseCsv(csv.byteInputStream())

        assertTrue(result.rows.isEmpty())
        assertTrue(result.errors.isNotEmpty())
    }
}
