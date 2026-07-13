package com.example.expensetracker.chattool;

public record RecurringExpenseStatusArguments(boolean includeInactive)
    implements ChatToolArguments {
}
