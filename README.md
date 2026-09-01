# Personal Finance

A native Android app for tracking personal income and expenses, built with Kotlin, Jetpack
Compose, and Room.

## Features

- **Dashboard** — total balance, this month's income/expense totals, a donut chart of spending
  by category, and a list of recent transactions.
- **Transactions** — full history grouped by day, with filter chips for type (income/expense)
  and category; long-press a transaction to delete it.
- **Add / edit transaction** — amount, income/expense toggle, category picker, date picker
  (Material3 date picker), and an optional note.
- **Categories** — built-in starter categories (Salary, Food, Transport, Bills, etc.), plus
  create your own with a custom name, color, and icon.
- Local-only storage via Room (SQLite) — no account, no network access, no ads.
- Material 3 theming with dynamic color (Android 12+) and light/dark support.

## Project structure

```
app/src/main/java/com/example/personalfinance/
├── data/                 # Room entities, DAOs, database, repository
├── ui/
│   ├── theme/            # Material3 color scheme, typography
│   ├── navigation/       # NavHost, bottom nav, routes
│   ├── dashboard/        # Dashboard screen + ViewModel
│   ├── transactions/     # Transaction list screen + ViewModel
│   ├── addtransaction/   # Add/edit transaction screen + ViewModel
│   ├── categories/       # Category management screen + ViewModel
│   └── common/           # Shared composables (TransactionRow), icon map, formatting, DI factory
├── MainActivity.kt
└── PersonalFinanceApp.kt # Application class; owns the Room DB + repository
```

Architecture is a straightforward MVVM: each screen has a `ViewModel` exposing a single
`StateFlow<UiState>` built by combining Room's `Flow` queries, and a `Composable` screen that
collects it. There's no DI framework — `PersonalFinanceApp` builds one `FinanceRepository` and
each screen's `ViewModel` is created through a small manual `ViewModelFactory`.

## Building and running

You'll need [Android Studio](https://developer.android.com/studio) (Hedgehog or newer) with the
Android SDK for API 34 installed, or the command line SDK + JDK 17.

```bash
./gradlew assembleDebug     # build a debug APK
./gradlew installDebug      # build and install on a connected device/emulator
```

Or open the project root in Android Studio and press Run.

Minimum SDK is 26 (Android 8.0), so no Java 8 desugaring is needed for `java.time`, which the
app uses throughout for dates.

> **Note on this session:** this project was written in a sandboxed environment without network
> access to Google's Maven repository (`dl.google.com`), which hosts the Android Gradle Plugin
> and all AndroidX/Compose libraries. That means it could not actually be compiled here — the
> Gradle wrapper is set up and every file was reviewed carefully by hand, but please run a build
> in Android Studio (or `./gradlew assembleDebug` with SDK access) as the first step, in case
> anything needs a small fix.

## Data model

- `Transaction`: amount, type (INCOME/EXPENSE), category (nullable FK), date, note.
- `Category`: name, type, color, icon key, whether it's a built-in default (defaults can't be
  deleted, only custom categories can).

Everything is stored locally in a SQLite database (`personal_finance.db`) via Room; there is no
sync or backend.
