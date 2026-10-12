# Status

_Last updated: 2026-10-12_

## Done

- Transactions: add/edit, categories with accordion picker, learned category rules, search.
- Dashboard, category overview, trends (6/12 months, per year), recurring/upcoming expenses.
- Spreadsheet (.xlsx) import/export, duplicate filtering, balance reconciliation.
- Full backup/restore; automatic weekly backup to OneDrive, Google Drive or the phone.
- Bank sync via Enable Banking (Sydbank), incl. the bank's scheduled payments.
- AI: Gemini → Groq → Claude fallback chain; Ask AI as a finance advisor that can act.
- "Needs your attention": dashboard alerts, phone notifications, loan/goal monitoring.
- Theme selector, main-account default, transfers excluded from income.

## In progress

- Nothing open. Last merged work: PR #5 (stop flagging normal payments at regular places
  as unusual).

## Known bugs

- None tracked in the repo yet (no open issues or TODOs in the code).
- Risk: Room uses `fallbackToDestructiveMigration()`, so any DB schema bump wipes user data.

## Next 3 tasks

1. Replace destructive migration with real Room migrations (and enable `exportSchema`).
2. Add instrumented/UI tests for the main flows (there are only JVM unit tests today).
3. Start tracking bugs as GitHub issues and keep this file in sync.
