package com.example.expensetracker.chattool;

import java.time.LocalDate;

public record CategoryBreakdownArguments(LocalDate fromDate, LocalDate toDate)
    implements ChatToolArguments {
}
