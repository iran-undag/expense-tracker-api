package com.example.expensetracker.chattool;

import com.example.expensetracker.model.Expense;
import com.example.expensetracker.model.ExpenseCategory;
import com.example.expensetracker.model.RecurringExpense;
import com.example.expensetracker.service.BudgetService;
import com.example.expensetracker.service.CategoryService;
import com.example.expensetracker.service.ChatIdentityMappingService;
import com.example.expensetracker.service.ExpenseFilterCriteria;
import com.example.expensetracker.service.ExpenseService;
import com.example.expensetracker.service.RecurringExpenseService;
import com.example.expensetracker.service.ReportService;
import com.example.expensetracker.persistence.DataRealm;
import com.example.expensetracker.persistence.DataRealmExecutor;
import com.example.expensetracker.security.UserDataScope;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class ChatToolService {
    private static final int MAX_LIST_RESULTS = 100;

    private final ChatToolRequestValidator validator;
    private final ChatIdentityMappingService mappingService;
    private final RecurringExpenseService recurringExpenseService;
    private final CategoryService categoryService;
    private final ReportService reportService;
    private final BudgetService budgetService;
    private final ExpenseService expenseService;
    private final Clock clock;
    private final DataRealmExecutor realmExecutor;

    public ChatToolService(
        ChatToolRequestValidator validator,
        ChatIdentityMappingService mappingService,
        RecurringExpenseService recurringExpenseService,
        CategoryService categoryService,
        ReportService reportService,
        BudgetService budgetService,
        ExpenseService expenseService,
        Clock clock,
        DataRealmExecutor realmExecutor
    ) {
        this.validator = validator;
        this.mappingService = mappingService;
        this.recurringExpenseService = recurringExpenseService;
        this.categoryService = categoryService;
        this.reportService = reportService;
        this.budgetService = budgetService;
        this.expenseService = expenseService;
        this.clock = clock;
        this.realmExecutor = realmExecutor;
    }

    public ChatToolResponse execute(ChatToolRequest request, Instant now) {
        ValidatedChatToolRequest validated = validator.validate(request);
        DataRealm realm = validated.directLineUserId().startsWith("dl_demo_")
            ? DataRealm.DEMO : DataRealm.PRIMARY;
        return realmExecutor.inRealm(realm, () -> executeInRealm(validated, now));
    }

    private ChatToolResponse executeInRealm(ValidatedChatToolRequest validated, Instant now) {
        UserDataScope scope = mappingService.resolveDataScope(
                validated.directLineUserId(), validated.conversationId(), now)
            .orElseThrow(ChatIdentityNotFoundException::new);
        recurringExpenseService.generateDueExpenses(scope, LocalDate.now(clock));

        Object result = switch (validated.tool()) {
            case MONTHLY_SUMMARY -> monthlySummary(scope, validated.arguments());
            case CATEGORY_BREAKDOWN -> categoryBreakdown(scope, validated.arguments());
            case SPENDING_TREND -> spendingTrend(scope, validated.arguments());
            case BUDGET_STATUS -> budgetStatus(scope, validated.arguments());
            case EXPENSE_LOOKUP -> expenseLookup(scope, validated.arguments());
            case RECURRING_EXPENSE_STATUS -> recurringExpenseStatus(scope, validated.arguments());
            case CATEGORY_LIST -> categoryList(scope, validated.arguments());
            case SPENDING_BY_PERIOD -> spendingByPeriod(scope, validated.arguments());
        };
        return new ChatToolResponse(validated.tool(), result);
    }

    private Object monthlySummary(UserDataScope scope, ChatToolArguments arguments) {
        MonthlySummaryArguments value = (MonthlySummaryArguments) arguments;
        return reportService.getMonthlySummary(scope, value.year(), value.month());
    }

    private Object categoryBreakdown(UserDataScope scope, ChatToolArguments arguments) {
        CategoryBreakdownArguments value = (CategoryBreakdownArguments) arguments;
        return reportService.getCategoryBreakdown(scope, value.fromDate(), value.toDate());
    }

    private Object spendingTrend(UserDataScope scope, ChatToolArguments arguments) {
        SpendingTrendArguments value = (SpendingTrendArguments) arguments;
        return reportService.getSpendingTrend(
            scope, value.year(), value.month(), value.months(), normalize(value.category()));
    }

    private Object budgetStatus(UserDataScope scope, ChatToolArguments arguments) {
        BudgetStatusArguments value = (BudgetStatusArguments) arguments;
        return budgetService.getBudgetSummary(scope, value.year(), value.month());
    }

    private ChatExpensePage expenseLookup(UserDataScope scope, ChatToolArguments arguments) {
        ExpenseLookupArguments value = (ExpenseLookupArguments) arguments;
        ExpenseFilterCriteria criteria = new ExpenseFilterCriteria(
            value.fromDate(), value.toDate(), normalize(value.category()),
            value.minAmount(), value.maxAmount(), normalize(value.query()));
        PageRequest pageRequest = PageRequest.of(value.page(), value.size(), expenseSort(value));
        Page<Expense> page = expenseService.getAllExpenses(scope, criteria, pageRequest);
        List<ChatExpenseResult> content = page.getContent().stream()
            .map(expense -> new ChatExpenseResult(
                expense.getId(), expense.getDescription(), expense.getAmount(),
                expense.getDate(), expense.getCategory()))
            .toList();
        return new ChatExpensePage(
            content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private Sort expenseSort(ExpenseLookupArguments value) {
        ExpenseSortBy sortBy = value.sortBy() == null ? ExpenseSortBy.DATE : value.sortBy();
        ExpenseSortDirection requested = value.sortDirection() == null
            ? ExpenseSortDirection.DESC : value.sortDirection();
        Sort.Direction direction = requested == ExpenseSortDirection.ASC
            ? Sort.Direction.ASC : Sort.Direction.DESC;
        if (sortBy == ExpenseSortBy.AMOUNT) {
            return Sort.by(direction, "amount")
                .and(Sort.by(Sort.Direction.DESC, "date"))
                .and(Sort.by(Sort.Direction.DESC, "id"));
        }
        return Sort.by(direction, "date").and(Sort.by(direction, "id"));
    }

    private ChatBoundedList<ChatRecurringExpenseResult> recurringExpenseStatus(
        UserDataScope scope, ChatToolArguments arguments
    ) {
        RecurringExpenseStatusArguments value = (RecurringExpenseStatusArguments) arguments;
        List<RecurringExpense> values = recurringExpenseService.getRecurringExpenses(scope);
        List<RecurringExpense> filtered = values.stream()
            .filter(rule -> value.includeInactive() || rule.isActive())
            .toList();
        List<ChatRecurringExpenseResult> results = filtered.stream()
            .map(rule -> new ChatRecurringExpenseResult(
                rule.getDescription(), rule.getAmount(), rule.getCategory(), rule.getFrequency(),
                rule.getStartDate(), rule.getEndDate(), rule.getNextRunDate(), rule.isActive()))
            .toList();
        return bounded(results);
    }

    private ChatBoundedList<ChatCategoryResult> categoryList(
        UserDataScope scope, ChatToolArguments arguments
    ) {
        CategoryListArguments value = (CategoryListArguments) arguments;
        List<ExpenseCategory> categories = categoryService.getCategories(
            scope, value.includeInactive());
        List<ChatCategoryResult> results = categories.stream()
            .map(category -> new ChatCategoryResult(
                category.getName(), category.isSystemDefault(), category.isActive()))
            .toList();
        return bounded(results);
    }

    private Object spendingByPeriod(UserDataScope scope, ChatToolArguments arguments) {
        SpendingByPeriodArguments value = (SpendingByPeriodArguments) arguments;
        return reportService.getSpendingByPeriod(
            scope, value.fromDate(), value.toDate(), value.granularity(),
            normalize(value.category()));
    }

    private <T> ChatBoundedList<T> bounded(List<T> values) {
        return new ChatBoundedList<>(
            values.stream().limit(MAX_LIST_RESULTS).toList(),
            values.size(),
            values.size() > MAX_LIST_RESULTS);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
