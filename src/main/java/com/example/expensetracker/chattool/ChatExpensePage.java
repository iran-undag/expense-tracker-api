package com.example.expensetracker.chattool;

import java.util.List;

public record ChatExpensePage(
    List<ChatExpenseResult> content, int page, int size, long totalElements, int totalPages
) {
}
