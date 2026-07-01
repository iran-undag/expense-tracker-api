package com.example.expensetracker.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.expensetracker.dto.ImportResultDto;
import com.example.expensetracker.model.Expense;
import com.example.expensetracker.repository.BudgetRepository;
import com.example.expensetracker.repository.ExpenseCategoryRepository;
import com.example.expensetracker.repository.ExpenseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class ImportExportServiceIntegrationTest {

    @Autowired
    private ImportExportService importExportService;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private ExpenseCategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        budgetRepository.deleteAll();
        categoryRepository.deleteAll();
        expenseRepository.deleteAll();
    }

    @Test
    void exportExpensesCsv_shouldIncludeOnlyCurrentUsersRecordsInDateRange() {
        expenseRepository.save(expense("user-a", "Lunch, with tax", "Food", "150.00", LocalDate.of(2026, 6, 1)));
        expenseRepository.save(expense("user-a", "Old", "Food", "75.00", LocalDate.of(2026, 5, 31)));
        expenseRepository.save(expense("user-b", "Taxi", "Transport", "80.00", LocalDate.of(2026, 6, 1)));

        String exported = importExportService.exportExpensesCsv(
            "user-a",
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 6, 30)
        );

        assertThat(exported).startsWith("date,description,category,amount\n");
        assertThat(exported).contains("2026-06-01,\"Lunch, with tax\",Food,150.00");
        assertThat(exported).doesNotContain("Old");
        assertThat(exported).doesNotContain("Taxi");
    }

    @Test
    void importExpensesCsv_shouldImportValidRowsAndReportInvalidRows() {
        String csv = """
            date,description,category,amount
            2026-06-01,Music,Subscriptions,149.00
            2026-06-02,Invalid,Subscriptions,0
            """;

        ImportResultDto result = importExportService.importExpensesCsv("user-a", csv);

        assertThat(result.getImportedExpenses()).isEqualTo(1);
        assertThat(result.getImportedBudgets()).isZero();
        assertThat(result.getImportedCategories()).isZero();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(expenseRepository.findByUserid("user-a")).hasSize(1);
    }

    private Expense expense(String userId, String description, String category, String amount, LocalDate date) {
        return Expense.builder()
            .userid(userId)
            .description(description)
            .category(category)
            .amount(new BigDecimal(amount))
            .date(date)
            .build();
    }
}
