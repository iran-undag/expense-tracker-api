package com.example.expensetracker.chattool;

public sealed interface ChatToolArguments permits MonthlySummaryArguments,
    CategoryBreakdownArguments, SpendingTrendArguments, BudgetStatusArguments,
    ExpenseLookupArguments, RecurringExpenseStatusArguments, CategoryListArguments,
    SpendingByPeriodArguments {
}
