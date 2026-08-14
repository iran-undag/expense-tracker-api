package com.example.expensetracker.demo.seed;

import com.example.expensetracker.model.Budget;
import com.example.expensetracker.model.Expense;
import com.example.expensetracker.model.ExpenseCategory;
import com.example.expensetracker.model.RecurringExpense;
import com.example.expensetracker.model.RecurringFrequency;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class DemoSeedTemplate {

    public static final int VERSION = 1;
    private static final String SEED_OWNER = "demo:seed";

    private static final List<String> CATEGORY_NAMES = List.of(
        "Food", "Groceries", "Transport", "Electricity", "Water", "Internet", "Phone",
        "Healthcare", "Shopping", "Travel", "Entertainment", "Mortgage", "Rent",
        "Insurance", "Tuition", "Other"
    );
    private static final List<String> CATEGORY_COLORS = List.of(
        "#4f6bed", "#16a34a", "#0ea5e9", "#f59e0b", "#06b6d4", "#7c3aed", "#ec4899",
        "#dc2626", "#e6007e", "#8b6fcb", "#2aa7a5", "#92400e", "#475569", "#0369a1",
        "#7e22ce", "#64748b"
    );
    private static final List<String> EXPENSE_DESCRIPTIONS = List.of(
        "Neighborhood bakery", "Weekly groceries", "Bus and train fare", "Electric bill",
        "Mobile plan", "Clinic visit", "Household supplies", "Weekend movie",
        "Coffee with friends", "Pharmacy essentials", "Online subscription", "Local market"
    );
    private static final List<String> EXPENSE_CATEGORIES = List.of(
        "Food", "Groceries", "Transport", "Electricity", "Phone", "Healthcare",
        "Shopping", "Entertainment", "Food", "Healthcare", "Internet", "Groceries"
    );
    private static final List<BigDecimal> EXPENSE_AMOUNTS = List.of(
        amount("145.50"), amount("1280.25"), amount("96.00"), amount("2140.70"),
        amount("599.00"), amount("850.00"), amount("425.75"), amount("320.00"),
        amount("185.00"), amount("275.40"), amount("249.00"), amount("735.20")
    );

    public SeedData generate(YearMonth anchorMonth) {
        List<Expense> expenses = new ArrayList<>();
        for (int monthOffset = 5; monthOffset >= 0; monthOffset--) {
            YearMonth month = anchorMonth.minusMonths(monthOffset);
            int count = monthOffset == 0 ? 25 : 12;
            for (int index = 0; index < count; index++) {
                int templateIndex = (index + monthOffset * 3) % EXPENSE_DESCRIPTIONS.size();
                int day = 1 + ((index * 7 + monthOffset * 5) % month.lengthOfMonth());
                expenses.add(Expense.builder()
                    .description(EXPENSE_DESCRIPTIONS.get(templateIndex) + " " + (index + 1))
                    .amount(EXPENSE_AMOUNTS.get(templateIndex))
                    .date(month.atDay(day))
                    .category(EXPENSE_CATEGORIES.get(templateIndex))
                    .userid(SEED_OWNER)
                    .demoSessionId(null)
                    .demoSeed(true)
                    .build());
            }
        }

        List<ExpenseCategory> categories = new ArrayList<>();
        for (int index = 0; index < CATEGORY_NAMES.size(); index++) {
            String name = CATEGORY_NAMES.get(index);
            categories.add(ExpenseCategory.builder()
                .userid(SEED_OWNER)
                .name(name)
                .color(CATEGORY_COLORS.get(index))
                .icon(name.toLowerCase())
                .systemDefault(true)
                .active(true)
                .demoSessionId(null)
                .demoSeed(true)
                .build());
        }

        List<Budget> budgets = List.of(
            budget(anchorMonth, "Food", "6000.00"),
            budget(anchorMonth, "Groceries", "9000.00"),
            budget(anchorMonth, "Transport", "4500.00"),
            budget(anchorMonth, "Entertainment", "2500.00"),
            budget(anchorMonth, "Shopping", "3500.00")
        );
        List<RecurringExpense> recurringExpenses = List.of(
            recurring(anchorMonth, "Home internet", "1699.00", "Internet", 5, true),
            recurring(anchorMonth, "Monthly insurance", "2400.00", "Insurance", 15, true),
            recurring(anchorMonth, "Completed tuition plan", "5000.00", "Tuition", 10, false)
        );
        return new SeedData(expenses, budgets, categories, recurringExpenses);
    }

    private static Budget budget(YearMonth anchorMonth, String category, String value) {
        return Budget.builder()
            .userid(SEED_OWNER)
            .category(category)
            .budgetYear(anchorMonth.getYear())
            .budgetMonth(anchorMonth.getMonthValue())
            .amount(amount(value))
            .demoSessionId(null)
            .demoSeed(true)
            .build();
    }

    private static RecurringExpense recurring(
        YearMonth anchorMonth,
        String description,
        String value,
        String category,
        int nextMonthDay,
        boolean active
    ) {
        return RecurringExpense.builder()
            .userid(SEED_OWNER)
            .description(description)
            .amount(amount(value))
            .category(category)
            .frequency(RecurringFrequency.MONTHLY)
            .startDate(anchorMonth.minusMonths(2).atDay(1))
            .nextRunDate(active
                ? anchorMonth.plusMonths(1).atDay(nextMonthDay)
                : anchorMonth.atDay(nextMonthDay))
            .active(active)
            .demoSessionId(null)
            .demoSeed(true)
            .build();
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    public record SeedData(
        List<Expense> expenses,
        List<Budget> budgets,
        List<ExpenseCategory> categories,
        List<RecurringExpense> recurringExpenses
    ) {
    }
}
