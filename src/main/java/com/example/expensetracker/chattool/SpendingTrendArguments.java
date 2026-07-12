package com.example.expensetracker.chattool;

public record SpendingTrendArguments(int year, int month, int months, String category)
    implements ChatToolArguments {
}
