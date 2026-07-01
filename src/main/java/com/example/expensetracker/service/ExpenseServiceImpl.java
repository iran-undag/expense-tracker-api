package com.example.expensetracker.service;

import com.example.expensetracker.model.Expense;
import com.example.expensetracker.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository expenseRepository;

    @Override
    @Transactional
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
    public Page<Expense> getAllExpenses(String userId, ExpenseFilterCriteria filters, Pageable pageable) {
        return expenseRepository.findAll(matchesFilters(userId, filters), pageable);
    }

    @Override
    public Page<Expense> getExpensesForMonth(int year, int month, String userId, Pageable pageable) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        return expenseRepository.findByUseridAndDateBetween(userId, startDate, endDate, pageable);
    }

    @Override
    @Transactional
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
    @Transactional
    public void deleteExpense(Long id, String userId) {
        log.info("Deleting expense {} for user {}", id, userId);
        Expense existing = expenseRepository.findByIdAndUserid(id, userId)
                .orElseThrow(() -> new RuntimeException("Expense not found or you do not have permission to delete it"));
        expenseRepository.delete(existing);
    }

    private Specification<Expense> matchesFilters(String userId, ExpenseFilterCriteria filters) {
        return (root, query, criteriaBuilder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(criteriaBuilder.equal(root.get("userid"), userId));

            if (filters == null) {
                return criteriaBuilder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
            }

            if (filters.fromDate() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("date"), filters.fromDate()));
            }
            if (filters.toDate() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("date"), filters.toDate()));
            }
            if (filters.category() != null && !filters.category().isBlank()) {
                predicates.add(criteriaBuilder.equal(
                    criteriaBuilder.lower(root.get("category")),
                    filters.category().trim().toLowerCase()
                ));
            }
            if (filters.minAmount() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("amount"), filters.minAmount()));
            }
            if (filters.maxAmount() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("amount"), filters.maxAmount()));
            }
            if (filters.query() != null && !filters.query().isBlank()) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("description")),
                    "%" + filters.query().trim().toLowerCase() + "%"
                ));
            }

            return criteriaBuilder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
