package com.example.expensetracker.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.expensetracker.model.RecurringExpense;
import com.example.expensetracker.model.RecurringFrequency;
import com.example.expensetracker.repository.ExpenseRepository;
import com.example.expensetracker.repository.RecurringExpenseOccurrenceRepository;
import com.example.expensetracker.repository.RecurringExpenseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class RecurringExpenseServiceIntegrationTest {

    @Autowired
    private RecurringExpenseService recurringExpenseService;

    @Autowired
    private RecurringExpenseRepository recurringExpenseRepository;

    @Autowired
    private RecurringExpenseOccurrenceRepository occurrenceRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @BeforeEach
    void setUp() {
        occurrenceRepository.deleteAll();
        recurringExpenseRepository.deleteAll();
        expenseRepository.deleteAll();
    }

    @Test
    void generateDueExpenses_shouldGenerateMissingOccurrencesOnce() {
        recurringExpenseService.saveRecurringExpense("testuser", RecurringExpense.builder()
            .description("Subscription")
            .amount(new BigDecimal("249.00"))
            .category("Entertainment")
            .frequency(RecurringFrequency.MONTHLY)
            .startDate(LocalDate.of(2026, 4, 15))
            .active(true)
            .build());

        int firstRun = recurringExpenseService.generateDueExpenses("testuser", LocalDate.of(2026, 6, 30));
        int secondRun = recurringExpenseService.generateDueExpenses("testuser", LocalDate.of(2026, 6, 30));

        assertThat(firstRun).isEqualTo(3);
        assertThat(secondRun).isZero();
        assertThat(expenseRepository.findByUserid("testuser"))
            .extracting(expense -> expense.getDate())
            .containsExactly(
                LocalDate.of(2026, 4, 15),
                LocalDate.of(2026, 5, 15),
                LocalDate.of(2026, 6, 15)
            );
        assertThat(recurringExpenseRepository.findByUseridOrderByActiveDescNextRunDateAsc("testuser"))
            .singleElement()
            .satisfies(rule -> {
                assertThat(rule.getNextRunDate()).isEqualTo(LocalDate.of(2026, 7, 15));
                assertThat(rule.isActive()).isTrue();
            });
    }

    @Test
    void generateDueExpenses_shouldDeactivateRuleAfterEndDate() {
        recurringExpenseService.saveRecurringExpense("testuser", RecurringExpense.builder()
            .description("Trial")
            .amount(new BigDecimal("99.00"))
            .category("Software")
            .frequency(RecurringFrequency.WEEKLY)
            .startDate(LocalDate.of(2026, 6, 1))
            .endDate(LocalDate.of(2026, 6, 8))
            .active(true)
            .build());

        int generated = recurringExpenseService.generateDueExpenses("testuser", LocalDate.of(2026, 6, 30));

        assertThat(generated).isEqualTo(2);
        assertThat(recurringExpenseRepository.findByUseridOrderByActiveDescNextRunDateAsc("testuser"))
            .singleElement()
            .satisfies(rule -> assertThat(rule.isActive()).isFalse());
    }
}
