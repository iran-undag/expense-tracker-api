package com.example.expensetracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.example.expensetracker.model.Expense;
import com.example.expensetracker.repository.ExpenseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @InjectMocks
    private ExpenseServiceImpl expenseService;

    @Test
    void saveExpense_shouldSetDateIfNotProvided() {
        Expense expense = new Expense();
        expense.setDescription("Test");
        expense.setAmount(new BigDecimal("10.00"));
        expense.setUserid("testuser");

        when(expenseRepository.save(any(Expense.class))).thenAnswer(
            invocation -> invocation.getArgument(0)
        );

        Expense saved = expenseService.saveExpense(expense);

        assertThat(saved.getDate()).isEqualTo(LocalDate.now());
        verify(expenseRepository).save(expense);
    }

    @Test
    void getExpenseById_shouldReturnExpense() {
        Expense expense = new Expense();
        expense.setId(1L);
        when(expenseRepository.findByIdAndUserid(1L, "testuser")).thenReturn(
            Optional.of(expense)
        );

        Optional<Expense> result = expenseService.getExpenseById(
            1L,
            "testuser"
        );

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(1L);
    }

    @Test
    void getAllExpenses_shouldReturnPage() {
        List<Expense> expenses = List.of(new Expense(), new Expense());
        PageRequest pageable = PageRequest.of(0, 10);
        when(expenseRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(
            new PageImpl<>(expenses, pageable, expenses.size())
        );

        Page<Expense> result = expenseService.getAllExpenses(
            "testuser",
            new ExpenseFilterCriteria(null, null, null, null, null, null),
            pageable
        );

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void getTotalExpensesForMonth_shouldCalculateTotal() {
        LocalDate start = LocalDate.of(2024, 5, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        Expense e1 = new Expense();
        e1.setAmount(new BigDecimal("10.00"));
        Expense e2 = new Expense();
        e2.setAmount(new BigDecimal("20.00"));

        when(
            expenseRepository.findByUseridAndDateBetween("testuser", start, end)
        ).thenReturn(List.of(e1, e2));

        BigDecimal total = expenseService.getTotalExpensesForMonth(
            2024,
            5,
            "testuser"
        );

        assertThat(total).isEqualTo(new BigDecimal("30.00"));
    }

    @Test
    void getExpensesForMonth_shouldReturnDateRangePage() {
        LocalDate start = LocalDate.of(2024, 5, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        PageRequest pageable = PageRequest.of(0, 10);
        Expense expense = new Expense();
        when(
            expenseRepository.findByUseridAndDateBetween(
                "testuser",
                start,
                end,
                pageable
            )
        ).thenReturn(new PageImpl<>(List.of(expense), pageable, 1));

        Page<Expense> result = expenseService.getExpensesForMonth(
            2024,
            5,
            "testuser",
            pageable
        );

        assertThat(result.getContent()).containsExactly(expense);
        verify(expenseRepository).findByUseridAndDateBetween(
            "testuser",
            start,
            end,
            pageable
        );
    }

}
