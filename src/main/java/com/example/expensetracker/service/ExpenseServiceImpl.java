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
    public Optional<Expense> getExpenseById(Long id) {
        return expenseRepository.findById(id);
    }

    @Override
    public List<Expense> getExpensesByDate(LocalDate date) {
        return expenseRepository.findByDate(date);
    }

    @Override
    public BigDecimal getTotalExpensesForMonth(int year, int month) {
        log.info("Calculating total expenses for month: {}/{}", month, year);
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        
        BigDecimal total = expenseRepository.findByDateBetween(startDate, endDate)
                .stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        log.info("Total expenses for {}/{}: {}", month, year, total);
        return total;
    }

    @Override
    public List<Expense> getAllExpenses() {
        return expenseRepository.findAll();
    }
}
