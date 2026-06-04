package com.example.expensetracker.service;

import com.example.expensetracker.model.Expense;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptResponseParserTest {

    private final ReceiptResponseParser parser = new ReceiptResponseParser(new ObjectMapper());

    @Test
    void parse_withFencedJson_shouldExtractExpense() {
        String content = """
                ```json
                {
                  "merchantName": "SOUTH SUPERMARKET",
                  "amount": 2466.65,
                  "date": "2023-04-05",
                  "category": "GROCERY SHOPPING"
                }
                ```
                """;

        Expense expense = parser.parse(content);

        assertThat(expense.getDescription()).isEqualTo("SOUTH SUPERMARKET");
        assertThat(expense.getAmount()).isEqualByComparingTo(new BigDecimal("2466.65"));
        assertThat(expense.getDate()).isEqualTo(LocalDate.of(2023, 4, 5));
        assertThat(expense.getCategory()).isEqualTo("GROCERY SHOPPING");
    }

    @Test
    void parse_withMonthFirstSlashDate_shouldUseMmDdYyyy() {
        String content = """
                {
                  "merchantName": "SOUTH SUPERMARKET",
                  "amount": 2466.65,
                  "date": "04/05/2023",
                  "category": "GROCERY SHOPPING"
                }
                """;

        Expense expense = parser.parse(content);

        assertThat(expense.getDate()).isEqualTo(LocalDate.of(2023, 4, 5));
    }

    @Test
    void parse_withKeyValueFallback_shouldExtractExpense() {
        String content = """
                merchantName: Corner Cafe
                total: $18.25
                date: 04/05/2023
                category: Food
                """;

        Expense expense = parser.parse(content);

        assertThat(expense.getDescription()).isEqualTo("Corner Cafe");
        assertThat(expense.getAmount()).isEqualByComparingTo(new BigDecimal("18.25"));
        assertThat(expense.getDate()).isEqualTo(LocalDate.of(2023, 4, 5));
        assertThat(expense.getCategory()).isEqualTo("Food");
    }
}
