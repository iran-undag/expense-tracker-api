package com.example.expensetracker.chattool;

import java.util.List;

public record ChatBoundedList<T>(List<T> content, long totalCount, boolean truncated) {
}
