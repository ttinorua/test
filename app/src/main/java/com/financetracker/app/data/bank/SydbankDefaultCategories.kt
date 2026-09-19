package com.financetracker.app.data.bank

import com.financetracker.app.data.db.entity.TransactionType

/**
 * Sydbank's own category taxonomy — extracted directly from a real Sydbank account's
 * transaction history (a spreadsheet export carrying the bank's own MainCategory and Category
 * columns), not a guessed list. Enable Banking's live sync API sends no category data at all
 * (see [com.financetracker.app.data.enablebanking.EnableBankingService.fetchTransactions]), so
 * without a category list this rich to pick from, both the AI and
 * [com.financetracker.app.data.ai.LocalCategoryMatcher] were correctly leaving a large share of
 * real transactions (furniture and general retail, fuel specifically, transfers, insurance/union
 * fees, and more) Uncategorized rather than force them into the wrong bucket — which looked like
 * "categorization barely does anything" even though the matching itself was working fine; there
 * was simply nothing good to match to.
 */
object SydbankDefaultCategories {

    val ALL = listOf(
        DefaultCategorySeed("Groceries", "Food", TransactionType.EXPENSE, "#2E7D32"),
        DefaultCategorySeed("Fuel", "Transportation", TransactionType.EXPENSE, "#66BB6A"),
        DefaultCategorySeed("Café, restaurant and bar", "Leisure", TransactionType.EXPENSE, "#EF6C00"),
        DefaultCategorySeed("Phone, internet, streaming and TV", "Media", TransactionType.EXPENSE, "#8D6E63"),
        DefaultCategorySeed("Parking", "Transportation", TransactionType.EXPENSE, "#5C6BC0"),
        DefaultCategorySeed("Furniture and home accessories", "Home", TransactionType.EXPENSE, "#26A69A"),
        DefaultCategorySeed("Other expense", "Other", TransactionType.EXPENSE, "#00897B"),
        DefaultCategorySeed("Take away and fast food", "Food", TransactionType.EXPENSE, "#00ACC1"),
        DefaultCategorySeed("Other income", "Income", TransactionType.INCOME, "#66BB6A"),
        DefaultCategorySeed("Films, music, apps and software", "Media", TransactionType.EXPENSE, "#AB47BC"),
        DefaultCategorySeed("Other (Transfer)", "Other", TransactionType.EXPENSE, "#D32F2F"),
        DefaultCategorySeed("Interest and fees", "Loan and debt", TransactionType.EXPENSE, "#F4511E"),
        DefaultCategorySeed("Pay, benefits and pension", "Income", TransactionType.INCOME, "#2E7D32"),
        DefaultCategorySeed("Electronics and gadgets", "Leisure", TransactionType.EXPENSE, "#78909C"),
        DefaultCategorySeed(
            "Clothing, shoes and accessories",
            "Clothing and pers. care prod.",
            TransactionType.EXPENSE,
            "#3949AB"
        ),
        DefaultCategorySeed("Taxis and public transportation", "Transportation", TransactionType.EXPENSE, "#00838F"),
        DefaultCategorySeed("Maintenance", "Home", TransactionType.EXPENSE, "#43A047"),
        DefaultCategorySeed(
            "Dentist, doctor and medication",
            "Clothing and pers. care prod.",
            TransactionType.EXPENSE,
            "#FB8C00"
        ),
        DefaultCategorySeed("Bakery, butcher, wine shop etc.", "Food", TransactionType.EXPENSE, "#7CB342"),
        DefaultCategorySeed("Electricity", "Home", TransactionType.EXPENSE, "#5D4037"),
        DefaultCategorySeed("Leisure (Other)", "Leisure", TransactionType.EXPENSE, "#546E7A"),
        DefaultCategorySeed("Sport and leisure activities", "Leisure", TransactionType.EXPENSE, "#8E24AA"),
        DefaultCategorySeed("Concert, cinema and museum", "Leisure", TransactionType.EXPENSE, "#C0CA33"),
        DefaultCategorySeed("Savings", "Savings and investment", TransactionType.EXPENSE, "#039BE5"),
        DefaultCategorySeed("Social security", "Income", TransactionType.INCOME, "#FFB300"),
        DefaultCategorySeed("Service and repair", "Transportation", TransactionType.EXPENSE, "#4527A0"),
        DefaultCategorySeed("Union and unemployment insurance", "Insurance", TransactionType.EXPENSE, "#00695C"),
        DefaultCategorySeed("Loan and debt (Other)", "Loan and debt", TransactionType.EXPENSE, "#E53935"),
        DefaultCategorySeed("Consumer loan", "Loan and debt", TransactionType.EXPENSE, "#3E2723"),
        DefaultCategorySeed("Hotel and caravanning", "Leisure", TransactionType.EXPENSE, "#455A64"),
        DefaultCategorySeed("Newspapers, magazines and books", "Media", TransactionType.EXPENSE, "#558B2F"),
        DefaultCategorySeed("Gifts and charity", "Leisure", TransactionType.EXPENSE, "#689F38"),
        DefaultCategorySeed(
            "Education and institution (Other)",
            "Education and institution",
            TransactionType.EXPENSE,
            "#EF6C00"
        ),
        DefaultCategorySeed("Interest groups", "Leisure", TransactionType.EXPENSE, "#8D6E63"),
        DefaultCategorySeed("Credit cards", "Loan and debt", TransactionType.EXPENSE, "#5C6BC0"),
        DefaultCategorySeed(
            "Hair and skin care",
            "Clothing and pers. care prod.",
            TransactionType.EXPENSE,
            "#26A69A"
        ),
        DefaultCategorySeed("Income (Other)", "Income", TransactionType.INCOME, "#00897B"),
        DefaultCategorySeed("Games and toys", "Leisure", TransactionType.EXPENSE, "#00ACC1"),
        DefaultCategorySeed("Interest and capital income", "Income", TransactionType.INCOME, "#EC407A"),
        DefaultCategorySeed(
            "No category (Credit card)",
            "Not included in budget and consumption",
            TransactionType.EXPENSE,
            "#AB47BC"
        ),
        DefaultCategorySeed("Loan and lease payment", "Transportation", TransactionType.EXPENSE, "#D32F2F"),
        DefaultCategorySeed("Transportation (Other)", "Transportation", TransactionType.EXPENSE, "#F4511E"),
        DefaultCategorySeed(
            "School, education and course expenses",
            "Education and institution",
            TransactionType.EXPENSE,
            "#6D4C41"
        ),
        DefaultCategorySeed("Pension savings", "Savings and investment", TransactionType.EXPENSE, "#78909C"),
        DefaultCategorySeed("Other (Cash)", "Other", TransactionType.EXPENSE, "#3949AB"),
        DefaultCategorySeed("Securities", "Savings and investment", TransactionType.EXPENSE, "#00838F"),
        DefaultCategorySeed(
            "Glasses and contact lenses",
            "Clothing and pers. care prod.",
            TransactionType.EXPENSE,
            "#43A047"
        ),
        DefaultCategorySeed("Holiday and travel", "Leisure", TransactionType.EXPENSE, "#FB8C00"),
        DefaultCategorySeed("Bicycles and other equipment", "Leisure", TransactionType.EXPENSE, "#7CB342"),
        DefaultCategorySeed("Bridge tolls and ferry ticket", "Transportation", TransactionType.EXPENSE, "#5D4037"),
        DefaultCategorySeed("Rent", "Home", TransactionType.EXPENSE, "#8D6E63"),
        DefaultCategorySeed("Uncategorized", "Uncategorized", TransactionType.EXPENSE, "#9E9E9E")
    )
}
