package com.example.expensetracker.service;

import com.example.expensetracker.model.Expense;
import com.example.expensetracker.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository expenseRepository;

    @Override
    public Expense saveExpense(Expense expense) {
        log.info("Connecting to database to save expense...");
        if (expense.getDate() == null) {
            expense.setDate(LocalDate.now());
        }
        Expense saved = expenseRepository.save(expense);
        log.info("Expense saved successfully with ID: {}", saved.getId());
        return saved;
    }

    @Override
    public Optional<Expense> getExpenseById(Long id, String userId) {
        return expenseRepository.findByIdAndUserid(id, userId);
    }

    @Override
    public List<Expense> getExpensesByDate(LocalDate date, String userId) {
        return expenseRepository.findByUseridAndDate(userId, date);
    }

    @Override
    public BigDecimal getTotalExpensesForMonth(int year, int month, String userId) {
        log.info("Calculating total expenses for month: {}/{} for user: {}", month, year, userId);
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        
        BigDecimal total = expenseRepository.findByUseridAndDateBetween(userId, startDate, endDate)
                .stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        log.info("Total expenses for {}/{}: {}", month, year, total);
        return total;
    }

    @Override
    public List<Expense> getAllExpenses(String userId) {
        return expenseRepository.findByUserid(userId);
    }

    @Override
    public Expense updateExpense(Long id, String userId, Expense updatedExpense) {
        log.info("Updating expense {} for user {}", id, userId);
        return expenseRepository.findByIdAndUserid(id, userId).map(existing -> {
            existing.setDescription(updatedExpense.getDescription());
            existing.setAmount(updatedExpense.getAmount());
            existing.setDate(updatedExpense.getDate());
            existing.setCategory(updatedExpense.getCategory());
            return expenseRepository.save(existing);
        }).orElseThrow(() -> new RuntimeException("Expense not found or you do not have permission to update it"));
    }

    @Override
    public void deleteExpense(Long id, String userId) {
        log.info("Deleting expense {} for user {}", id, userId);
        Expense existing = expenseRepository.findByIdAndUserid(id, userId)
                .orElseThrow(() -> new RuntimeException("Expense not found or you do not have permission to delete it"));
        expenseRepository.delete(existing);
    }
}
