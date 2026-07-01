# Expense Tracker Feature Plan

Last updated: 2026-06-30

This file is the durable roadmap/status document for the feature work discussed for the Expense Tracker workspace. Read this file first when continuing feature planning; it is intended to remove the need to reload the prior conversation.

## Workspace Context

The local workspace has three sibling apps:

- `expense-tracker-api`: Spring Boot API, database access, receipt processing orchestration, user-scoped data model.
- `expense-tracker-web`: Vue/Vite frontend dashboard.
- `expense-tracker-receipt`: Azure Function receipt extraction service. This roadmap did not require changes there.

Feature work in this plan primarily modifies `expense-tracker-api` and `expense-tracker-web`.

## Status Summary

| # | Feature | Status | Current State |
| --- | --- | --- | --- |
| 1 | Expense search and filtering | Implemented | API supports date/category/amount/query filters; web dashboard has filter controls. |
| 2 | Budget management | Implemented | API supports monthly budget CRUD and summaries; web dashboard has budget editor and budget-vs-actual rows. |
| 3 | Reports and dashboard analytics | Implemented | API exposes monthly summary, category breakdown, and trend endpoints; web dashboard renders analytics. |
| 4 | Recurring expenses | Implemented | API has recurring rules with generate-on-read; web dashboard manages recurring rules. |
| 5 | Import/export | Implemented | API exports/imports JSON for expenses, budgets, categories; web dashboard has import/export controls. |
| 6 | Category management | Implemented | API has category CRUD/soft delete/default categories; web dashboard manages category options. |
| 7 | Receipt draft/review queue and archive/history | Pending | Receipt extraction exists, but extracted receipts are not saved as drafts for later review and uploaded receipt images/review history are not stored or browsable. |
| 8 | Notifications and alerts | Pending | No alerting exists yet for budget thresholds, recurring generation, or unusual spend. |

## 1. Expense Search And Filtering

### Original Plan

Add server-side filtering so users can find expenses without loading all records into the browser. Filters should include date range, category, min/max amount, and text search over description. Then expose those filters in the dashboard.

### Implemented

API:

- `GET /api/expenses`
- Query parameters:
  - `fromDate`
  - `toDate`
  - `category`
  - `minAmount`
  - `maxAmount`
  - `query`
  - pageable params such as `page`, `size`, `sort`

Important API files:

- `src/main/java/com/example/expensetracker/controller/ExpenseController.java`
- `src/main/java/com/example/expensetracker/service/ExpenseFilterCriteria.java`
- `src/main/java/com/example/expensetracker/service/ExpenseServiceImpl.java`
- `src/main/java/com/example/expensetracker/repository/ExpenseRepository.java`

Web:

- Dashboard filter row supports search, from/to date, category, min amount, and max amount.
- Filter state is separate from applied state so users can edit filters before applying.

Important web files:

- `../expense-tracker-web/src/api/expenses.ts`
- `../expense-tracker-web/src/pages/DashboardPage.vue`
- `../expense-tracker-web/src/types/expense.ts`

### Pending

No required pending work. Optional later improvement: saved filter presets.

## 2. Budget Management

### Original Plan

Add monthly budget CRUD and a budget summary endpoint that compares budgeted amounts with actual spending for the selected month. Then add budget management to the dashboard.

### Implemented

API:

- `GET /api/budgets?year={year}&month={month}`
- `GET /api/budgets/summary?year={year}&month={month}`
- `POST /api/budgets`
- `PUT /api/budgets/{id}`
- `DELETE /api/budgets/{id}`

Behavior:

- Budgets are user-scoped.
- Budgets are keyed by user, year, month, and category.
- Saving a budget for an existing category/month updates the existing row.
- Summary includes budgeted amount, actual amount, remaining amount, percent used, and over-budget state.

Important API files:

- `src/main/java/com/example/expensetracker/controller/BudgetController.java`
- `src/main/java/com/example/expensetracker/model/Budget.java`
- `src/main/java/com/example/expensetracker/repository/BudgetRepository.java`
- `src/main/java/com/example/expensetracker/service/BudgetService.java`
- `src/main/java/com/example/expensetracker/service/BudgetServiceImpl.java`
- `src/main/resources/db/migration/V4__create_budget_table.sql`

Web:

- Dashboard has a budget editor for selected reporting month.
- Dashboard shows budgeted, actual, and remaining totals.
- Budget rows can be edited and deleted.

Important web files:

- `../expense-tracker-web/src/api/budgets.ts`
- `../expense-tracker-web/src/types/budget.ts`
- `../expense-tracker-web/src/pages/DashboardPage.vue`

### Pending

No required pending work. Optional later improvement: budget alerts, rollovers, and annual budgets.

## 3. Reports And Dashboard Analytics

### Original Plan

Add report endpoints for monthly summary, category breakdown, and spending trend. Then replace page-local summary calculations with API-driven analytics in the dashboard.

### Implemented

API:

- `GET /api/reports/monthly-summary?year={year}&month={month}`
- `GET /api/reports/category-breakdown?fromDate={date}&toDate={date}`
- `GET /api/reports/spending-trend?year={year}&month={month}&months={count}`

Important API files:

- `src/main/java/com/example/expensetracker/controller/ReportController.java`
- `src/main/java/com/example/expensetracker/service/ReportService.java`
- `src/main/java/com/example/expensetracker/service/ReportServiceImpl.java`
- `src/main/java/com/example/expensetracker/dto/MonthlySummaryDto.java`
- `src/main/java/com/example/expensetracker/dto/CategoryBreakdownDto.java`
- `src/main/java/com/example/expensetracker/dto/SpendingTrendDto.java`

Web:

- Dashboard shows monthly expense count, average spend, category donut/legend, and 6-month trend bars.

Important web files:

- `../expense-tracker-web/src/api/reports.ts`
- `../expense-tracker-web/src/types/report.ts`
- `../expense-tracker-web/src/pages/DashboardPage.vue`

### Pending

No required pending work. Optional later improvement: custom date ranges and CSV/PDF reports.

## 4. Recurring Expenses

### Original Plan

Support recurring expenses without relying on a background scheduler. Use generate-on-read first, because the API may run on sleep-prone/free-tier hosting where scheduled tasks are unreliable.

### Implemented

API:

- `GET /api/recurring-expenses`
- `POST /api/recurring-expenses`
- `PUT /api/recurring-expenses/{id}`
- `DELETE /api/recurring-expenses/{id}`

Behavior:

- Rules support `DAILY`, `WEEKLY`, `MONTHLY`, and `YEARLY`.
- Rules include description, amount, category, frequency, start date, optional end date, next run date, and active state.
- Read endpoints call `RecurringExpenseService.generateDueExpenses(userId, LocalDate.now())` before returning data.
- Generated occurrences are tracked by `recurring_expense_id + occurrence_date` to prevent duplicates.
- Generated rows are normal `Expense` records.
- Rules advance `nextRunDate` after generation and deactivate when past `endDate`.

Generate-on-read currently runs before:

- `GET /api/expenses`
- `GET /api/expenses/{id}`
- `GET /api/expenses/date/{date}`
- `GET /api/expenses/month/{year}/{month}`
- `GET /api/expenses/month/{year}/{month}/total`
- `GET /api/budgets`
- `GET /api/budgets/summary`
- all report endpoints
- `GET /api/recurring-expenses`

Important API files:

- `src/main/java/com/example/expensetracker/controller/RecurringExpenseController.java`
- `src/main/java/com/example/expensetracker/model/RecurringExpense.java`
- `src/main/java/com/example/expensetracker/model/RecurringExpenseOccurrence.java`
- `src/main/java/com/example/expensetracker/model/RecurringFrequency.java`
- `src/main/java/com/example/expensetracker/repository/RecurringExpenseRepository.java`
- `src/main/java/com/example/expensetracker/repository/RecurringExpenseOccurrenceRepository.java`
- `src/main/java/com/example/expensetracker/service/RecurringExpenseService.java`
- `src/main/java/com/example/expensetracker/service/RecurringExpenseServiceImpl.java`
- `src/main/resources/db/migration/V6__create_recurring_expense_tables.sql`
- `src/test/java/com/example/expensetracker/service/RecurringExpenseServiceIntegrationTest.java`

Web:

- Dashboard has a recurring expenses panel.
- Users can create, edit, pause/reactivate, and delete recurring rules.
- The panel uses active managed categories.

Important web files:

- `../expense-tracker-web/src/api/recurringExpenses.ts`
- `../expense-tracker-web/src/types/recurringExpense.ts`
- `../expense-tracker-web/src/pages/DashboardPage.vue`

### Pending

No required pending work. Optional later improvement: explicit "generate now" endpoint, skipped occurrence support, and editing future generated occurrences.

## 5. Import/Export

### Original Plan

Allow users to move expense records with CSV files. Export should include user-owned expense records for a selected date range; import should validate rows and avoid failing the entire file when one row is invalid.

### Implemented

API:

- `GET /api/import-export/export?fromDate={date}&toDate={date}`
- `POST /api/import-export/import`

Export includes:

- Expense records as CSV with `date`, `description`, `category`, and `amount` columns.

Import behavior:

- Expenses are appended from CSV rows.
- Invalid rows are returned as row-level errors in the import result.
- Import/export does not include budgets, categories, or recurring expense rules.

Important API files:

- `src/main/java/com/example/expensetracker/controller/ImportExportController.java`
- `src/main/java/com/example/expensetracker/service/ImportExportService.java`
- `src/main/java/com/example/expensetracker/service/ImportExportServiceImpl.java`
- `src/main/java/com/example/expensetracker/dto/ImportResultDto.java`
- `src/main/java/com/example/expensetracker/dto/ImportErrorDto.java`
- `src/test/java/com/example/expensetracker/service/ImportExportServiceIntegrationTest.java`

Web:

- Dashboard has an Import/Export tab.
- Export asks for start and end dates, then downloads `expense-records-{fromDate}-to-{toDate}.csv`.
- Import uses a file picker for `.csv` files and shows imported counts plus row-level errors.

Important web files:

- `../expense-tracker-web/src/api/importExport.ts`
- `../expense-tracker-web/src/types/importExport.ts`
- `../expense-tracker-web/src/pages/DashboardPage.vue`

### Pending

Add recurring expense rules to import/export if users need full backup/restore of future schedules.

## 6. Category Management

### Original Plan

Replace hardcoded category-only behavior with user-managed categories. Keep sensible defaults but allow users to add, edit, deactivate, and reuse categories across expense forms, filters, and budgets.

### Implemented

API:

- `GET /api/categories`
- `GET /api/categories?includeInactive=true`
- `POST /api/categories`
- `PUT /api/categories/{id}`
- `DELETE /api/categories/{id}`

Behavior:

- Categories are user-scoped.
- Default categories are lazily seeded.
- Name uniqueness is enforced per user.
- Delete is a soft delete via `active=false`.
- Inactive categories can be reactivated by creating the same name again.

Important API files:

- `src/main/java/com/example/expensetracker/controller/CategoryController.java`
- `src/main/java/com/example/expensetracker/model/ExpenseCategory.java`
- `src/main/java/com/example/expensetracker/repository/ExpenseCategoryRepository.java`
- `src/main/java/com/example/expensetracker/service/CategoryService.java`
- `src/main/java/com/example/expensetracker/service/CategoryServiceImpl.java`
- `src/main/resources/db/migration/V5__create_expense_category_table.sql`
- `src/test/java/com/example/expensetracker/service/CategoryServiceIntegrationTest.java`

Web:

- Dashboard has a category management panel.
- Active categories are used in the expense form, budget editor, recurring expense editor, and expense filters.
- Existing expenses keep their historical category text if a category is deactivated.

Important web files:

- `../expense-tracker-web/src/api/categories.ts`
- `../expense-tracker-web/src/types/category.ts`
- `../expense-tracker-web/src/components/ExpenseForm.vue`
- `../expense-tracker-web/src/pages/DashboardPage.vue`

### Pending

No required pending work. Optional later improvement: category rename propagation to existing expenses and budgets.

## 7. Receipt Draft/Review Queue And Archive/History

### Original Plan

Receipt extraction already exists, but the workflow is immediate and transient: upload a receipt, extract editable expense fields, then either save the expense or lose the extracted result. A future feature should support a receipt draft/review queue so users can upload receipts, review or correct extracted fields later, approve drafts into expenses, reject drafts, and keep an audit trail. The same feature should also provide receipt archive/history so users can browse saved receipts, see extracted values, and trace which expense came from which receipt.

### Status

Pending.

### Current Baseline

Existing behavior:

- Web uploads a receipt image.
- API extracts editable expense fields through the configured provider.
- The frontend asks the user to confirm before saving.
- The original image, extraction result, review state, and linked expense relationship are not stored as durable records.
- There is no queue for pending receipt drafts.
- If the user leaves the upload flow before saving, the extracted receipt data is lost.

Existing relevant files:

- `src/main/java/com/example/expensetracker/controller/ExpenseController.java`
- `src/main/java/com/example/expensetracker/service/ReceiptProcessor.java`
- `../expense-tracker-web/src/components/ReceiptUpload.vue`
- `../expense-tracker-web/src/components/ReceiptImageViewer.vue`
- `../expense-tracker-web/src/pages/DashboardPage.vue`

### Proposed Implementation

API:

- Add `ReceiptDraft` or `ExpenseReceipt` entity.
- Store receipt metadata: user id, original filename, content type, size, processing provider, status, extracted fields, linked expense id, created timestamp, reviewed timestamp.
- Use explicit statuses such as:
  - `PENDING_REVIEW`
  - `APPROVED`
  - `REJECTED`
  - `FAILED_EXTRACTION`
- Decide storage for image bytes:
  - SQL varbinary for small local demo scope, or
  - object/blob storage for production.
- Add endpoints:
  - `POST /api/receipt-drafts`
  - `GET /api/receipt-drafts?status=PENDING_REVIEW`
  - `GET /api/receipt-drafts/{id}`
  - `PUT /api/receipt-drafts/{id}`
  - `POST /api/receipt-drafts/{id}/approve`
  - `POST /api/receipt-drafts/{id}/reject`
  - `GET /api/receipt-drafts/{id}/image`
  - `DELETE /api/receipt-drafts/{id}`

Approval behavior:

- Approving a draft creates a normal `Expense` row from the reviewed fields.
- Approval links the draft to the created expense id.
- Re-approving an already approved draft must not create duplicate expenses.
- Rejected drafts remain visible in history unless deleted.

Web:

- Change receipt upload so extraction creates a pending draft instead of requiring immediate expense save.
- Add a receipt review queue filtered to pending drafts.
- Add draft detail/edit view with image preview and extracted fields.
- Add approve and reject actions.
- Add receipt history/archive filters for approved, rejected, and failed drafts.
- Link expenses created from approved drafts back to the receipt detail.

### Open Questions

- Should receipt images be stored in SQL Server, local disk, or blob storage?
- Should deleting an expense delete the linked receipt record?
- Should receipts be exportable?
- Should the upload flow still support immediate "save now", or should all uploaded receipts go through the draft queue?

## 8. Notifications And Alerts

### Original Plan

Notify users about conditions that need attention, such as nearing or exceeding budget, newly generated recurring expenses, import errors, and unusual spending.

### Status

Pending.

### Current Baseline

Existing behavior:

- Budget rows show over-budget state in the dashboard.
- Import shows row-level errors immediately after import.
- Recurring generation silently creates due expenses on read.
- There is no durable notification model, no notification center, and no outbound email/push mechanism.

### Proposed Implementation

API:

- Add `Notification` entity scoped by user.
- Add notification types such as:
  - `BUDGET_THRESHOLD`
  - `BUDGET_EXCEEDED`
  - `RECURRING_EXPENSE_GENERATED`
  - `IMPORT_COMPLETED_WITH_ERRORS`
  - `UNUSUAL_SPEND`
- Add endpoints:
  - `GET /api/notifications`
  - `PUT /api/notifications/{id}/read`
  - `PUT /api/notifications/read-all`
  - `DELETE /api/notifications/{id}`
- Generate notifications from existing service flows:
  - budget summary calculation,
  - recurring expense generation,
  - import completion.

Web:

- Add notification indicator in the app shell.
- Add notification list/panel.
- Let users mark notifications as read.

### Open Questions

- Should alerts be in-app only, or should email/push be added?
- What budget threshold should trigger a warning: 80%, 90%, or configurable?
- Should notifications be generated every read or deduplicated by period/type/entity?

## Verification Commands

Run these after changing API behavior:

```bash
cd /home/user/Documents/vscode-workspace/expense-tracker-api
./mvnw test -q
```

Run these after changing web behavior:

```bash
cd /home/user/Documents/vscode-workspace/expense-tracker-web
npm run typecheck
npm run build
```

Current known verification status after the implemented feature work:

- `./mvnw test -q` passes in `expense-tracker-api`.
- `npm run build` passes in `expense-tracker-web`.
- Vite reports a large chunk warning; the build is still valid.

## Notes For Future Agents

- Keep user ownership server-side. Do not accept `userid` from web payloads.
- Route frontend API calls through `src/api/http.ts`.
- When API contracts change, update both the API README and web README.
- If import/export is extended, update `ImportExportDto`, API service tests, web types, and this plan.
- If recurring expenses are extended, preserve idempotency through occurrence tracking.
- The parent workspace has `AGENTS.md` and `PROJECT.md`, but this `PLAN.md` currently lives in `expense-tracker-api` because that is a writable app root in this session.
