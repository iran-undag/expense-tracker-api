package com.example.expensetracker.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.expensetracker.model.Expense;
import com.example.expensetracker.repository.ExpenseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class ExpenseServiceFilterIntegrationTest {

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private ExpenseRepository expenseRepository;

    @BeforeEach
    void setUp() {
        expenseRepository.deleteAll();
        expenseRepository.saveAll(List.of(
            expense("testuser", "Lunch at Subway", "Food", "12.50", LocalDate.of(2026, 6, 10)),
            expense("testuser", "Bus fare", "Transport", "4.00", LocalDate.of(2026, 6, 11)),
            expense("testuser", "Dinner", "Food", "28.00", LocalDate.of(2026, 7, 1)),
            expense("otheruser", "Lunch at Subway", "Food", "15.00", LocalDate.of(2026, 6, 10))
        ));
    }

    @Test
    void getAllExpenses_shouldApplyCombinedFiltersAndUserScope() {
        Page<Expense> result = expenseService.getAllExpenses(
            "testuser",
            new ExpenseFilterCriteria(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                "food",
                new BigDecimal("10.00"),
                new BigDecimal("20.00"),
                "lunch"
            ),
            PageRequest.of(0, 10)
        );

        assertThat(result.getContent())
            .extracting(Expense::getDescription)
            .containsExactly("Lunch at Subway");
        assertThat(result.getContent())
            .extracting(Expense::getUserid)
            .containsExactly("testuser");
    }

    @Test
    void getAllExpenses_shouldSortAmountDescendingWithDateAndIdDescendingTies() {
        List<Expense> saved = expenseRepository.saveAll(List.of(
            expense("testuser", "Highest", "Amount sort", "30.00", LocalDate.of(2026, 8, 1)),
            expense("testuser", "Earlier tie", "Amount sort", "20.00", LocalDate.of(2026, 7, 31)),
            expense("testuser", "Later tie first", "Amount sort", "20.00", LocalDate.of(2026, 8, 1)),
            expense("testuser", "Later tie second", "Amount sort", "20.00", LocalDate.of(2026, 8, 1)),
            expense("otheruser", "Other owner", "Amount sort", "30.00", LocalDate.of(2026, 8, 2))
        ));
        Sort sort = Sort.by(Sort.Direction.DESC, "amount")
            .and(Sort.by(Sort.Direction.DESC, "date"))
            .and(Sort.by(Sort.Direction.DESC, "id"));

        Page<Expense> result = expenseService.getAllExpenses(
            "testuser",
            new ExpenseFilterCriteria(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 31), "Amount sort",
                new BigDecimal("20.00"), new BigDecimal("30.00"), null),
            PageRequest.of(0, 10, sort));

        assertThat(result.getContent())
            .extracting(Expense::getId)
            .containsExactly(
                saved.get(0).getId(), saved.get(3).getId(),
                saved.get(2).getId(), saved.get(1).getId());
        assertThat(result.getContent())
            .extracting(Expense::getUserid)
            .containsOnly("testuser");
    }

    @Test
    void getAllExpenses_shouldSortDateAscendingWithIdAscendingTies() {
        List<Expense> saved = expenseRepository.saveAll(List.of(
            expense("testuser", "Oldest first", "Date sort", "10.00", LocalDate.of(2026, 8, 1)),
            expense("testuser", "Oldest second", "Date sort", "20.00", LocalDate.of(2026, 8, 1)),
            expense("testuser", "Newest", "Date sort", "30.00", LocalDate.of(2026, 8, 2)),
            expense("otheruser", "Other owner older", "Date sort", "40.00", LocalDate.of(2026, 7, 31))
        ));
        Sort sort = Sort.by(Sort.Direction.ASC, "date")
            .and(Sort.by(Sort.Direction.ASC, "id"));

        Page<Expense> result = expenseService.getAllExpenses(
            "testuser",
            new ExpenseFilterCriteria(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 31), "Date sort",
                null, null, null),
            PageRequest.of(0, 10, sort));

        assertThat(result.getContent())
            .extracting(Expense::getId)
            .containsExactly(saved.get(0).getId(), saved.get(1).getId(), saved.get(2).getId());
        assertThat(result.getContent())
            .extracting(Expense::getUserid)
            .containsOnly("testuser");
    }

    private Expense expense(String userId, String description, String category, String amount, LocalDate date) {
        Expense expense = new Expense();
        expense.setUserid(userId);
        expense.setDescription(description);
        expense.setCategory(category);
        expense.setAmount(new BigDecimal(amount));
        expense.setDate(date);
        return expense;
    }
}
