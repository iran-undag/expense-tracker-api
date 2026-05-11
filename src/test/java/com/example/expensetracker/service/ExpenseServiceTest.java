package com.example.expensetracker.service;

import com.example.expensetracker.model.Expense;
import com.example.expensetracker.repository.ExpenseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Expense saved = expenseService.saveExpense(expense);

        assertThat(saved.getDate()).isEqualTo(LocalDate.now());
        verify(expenseRepository).save(expense);
    }

    @Test
    void getExpenseById_shouldReturnExpense() {
        Expense expense = new Expense();
        expense.setId(1L);
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));

        Optional<Expense> result = expenseService.getExpenseById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(1L);
    }

    @Test
    void getAllExpenses_shouldReturnList() {
        List<Expense> expenses = List.of(new Expense(), new Expense());
        when(expenseRepository.findAll()).thenReturn(expenses);

        List<Expense> result = expenseService.getAllExpenses();

        assertThat(result).hasSize(2);
    }

    @Test
    void getTotalExpensesForMonth_shouldCalculateTotal() {
        LocalDate start = LocalDate.of(2024, 5, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        
        Expense e1 = new Expense(); e1.setAmount(new BigDecimal("10.00"));
        Expense e2 = new Expense(); e2.setAmount(new BigDecimal("20.00"));
        
        when(expenseRepository.findByDateBetween(start, end)).thenReturn(List.of(e1, e2));

        BigDecimal total = expenseService.getTotalExpensesForMonth(2024, 5);

        assertThat(total).isEqualTo(new BigDecimal("30.00"));
    }
}
