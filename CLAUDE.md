# Working with the owner of this project

## Questions come before code

If a message contains a question mark ("?") or reads like a question, do not start coding.
Answer the question or clarify what's meant first, then wait for an explicit go-ahead before
changing anything.

- Reading code, checking build status or looking things up to answer the question is fine.
  Editing files, committing, pushing or starting a build is not.
- If a message mixes a request with a question (e.g. "Add X. Also, why does Y happen?"),
  answer the question, confirm what you're about to change, and wait for the go-ahead before
  coding any of it.

## How to work here

- Minimal diffs: change only what the task needs. No full-file rewrites.
- Keep replies short.
- Ask before adding any library or dependency.
- If a change touches more than 3 files, post a short plan first and wait for the go-ahead.
- Don't edit `.claude/` unless asked.

## Stack

- Android app, single module `:app`. Kotlin 2.0.21, Jetpack Compose (BOM 2024.09) + Material 3.
- AGP 8.6.1, Gradle 8.7 wrapper, JDK 17, minSdk 26, compile/targetSdk 34. KSP for Room.
- Libraries: Room 2.6.1, Navigation Compose, Lifecycle ViewModel, Coroutines/Flow, WorkManager,
  Apache POI (xlsx), Play Services Auth (Google Drive), anthropic-java. Tests: JUnit 4 + org.json.
- AI: Gemini → Groq → Claude fallback chain. Bank sync via Enable Banking.

## Build

- `./gradlew assembleDebug` · `./gradlew assembleRelease` (signed only if keystore env vars set)
- `./gradlew testDebugUnitTest` (JVM unit tests in `app/src/test`; no instrumented tests)
- Secrets come from `local.properties` or env vars (see `app/build.gradle.kts`), never the repo.

## Package structure (`app/src/main/java/com/financetracker/app`)

- `FinanceApp.kt` (inits prefs singletons, workers), `MainActivity.kt`
- `data/db` Room `AppDatabase`, `dao/`, `entity/` · `data/repository/FinanceRepository`
- `data/prefs` SharedPreferences-backed settings objects · `data/ai` AI services, categorisation,
  `agent/`, `advisor/` · `data/enablebanking` bank sync · `data/bank` bank/category definitions
- `data/importexport` spreadsheet import/export · `data/backup` full + auto backup (`cloud/`)
- `data/advisor` attention monitor · `ui/screens/<feature>` Screen + ViewModel per feature
- `ui/components` shared composables · `ui/navigation` · `ui/theme`
- `util` pure logic (periods, trends, filters, formatting) · `util/advisor` analysis checks

## Conventions

- No DI framework: ViewModels get dependencies via `util/ViewModelFactory`.
- ViewModels expose `StateFlow<…UiState>` built with `combine(...).stateIn(viewModelScope, …)`.
- Settings are `object`s with `init(context)` (called in `FinanceApp`) and a `StateFlow` per value.
- Pure logic lives in `util/` and is unit-tested in `app/src/test` (`<Name>Test.kt`).
- KDoc on classes explaining the *why*; plain-language user-facing commit messages.
- Room uses `fallbackToDestructiveMigration()` (pre-release): a schema bump wipes data.
