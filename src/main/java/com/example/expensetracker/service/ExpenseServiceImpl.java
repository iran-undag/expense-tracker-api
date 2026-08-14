package com.example.expensetracker.service;

import com.example.expensetracker.model.Expense;
import com.example.expensetracker.repository.ExpenseRepository;
import com.example.expensetracker.security.UserDataScope;
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
    public Expense saveExpense(UserDataScope scope, Expense expense) {
        log.info("Connecting to database to save expense...");
        expense.setUserid(scope.ownerId());
        expense.setDemoSessionId(scope.demoSessionId());
        expense.setDemoSeed(false);
        if (expense.getDate() == null) {
            expense.setDate(LocalDate.now());
        }
        Expense saved = expenseRepository.save(expense);
        log.info("Expense saved successfully with ID: {}", saved.getId());
        return saved;
    }

    @Override
    public Optional<Expense> getExpenseById(Long id, UserDataScope scope) {
        return expenseRepository.findByIdAndUseridIn(id, scope.readableOwnerIds());
    }

    @Override
    public List<Expense> getExpensesByDate(LocalDate date, UserDataScope scope) {
        return expenseRepository.findByUseridInAndDate(scope.readableOwnerIds(), date);
    }

    @Override
    public BigDecimal getTotalExpensesForMonth(int year, int month, UserDataScope scope) {
        log.info("Calculating total expenses for month: {}/{} for owner: {}", month, year, scope.ownerId());
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        
        BigDecimal total = expenseRepository.findByUseridInAndDateBetween(scope.readableOwnerIds(), startDate, endDate)
                .stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        log.info("Total expenses for {}/{}: {}", month, year, total);
        return total;
    }

    @Override
    public Page<Expense> getAllExpenses(UserDataScope scope, ExpenseFilterCriteria filters, Pageable pageable) {
        return expenseRepository.findAll(matchesFilters(scope, filters), pageable);
    }

    @Override
    public Page<Expense> getExpensesForMonth(int year, int month, UserDataScope scope, Pageable pageable) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        return expenseRepository.findByUseridInAndDateBetween(scope.readableOwnerIds(), startDate, endDate, pageable);
    }

    @Override
    @Transactional
    public Expense updateExpense(Long id, UserDataScope scope, Expense updatedExpense) {
        log.info("Updating expense {} for owner {}", id, scope.ownerId());
        return expenseRepository.findByIdAndUserid(id, scope.ownerId()).map(existing -> {
            existing.setDescription(updatedExpense.getDescription());
            existing.setAmount(updatedExpense.getAmount());
            existing.setDate(updatedExpense.getDate());
            existing.setCategory(updatedExpense.getCategory());
            return expenseRepository.save(existing);
        }).orElseThrow(() -> new RuntimeException("Expense not found or you do not have permission to update it"));
    }

    @Override
    @Transactional
    public void deleteExpense(Long id, UserDataScope scope) {
        log.info("Deleting expense {} for owner {}", id, scope.ownerId());
        Expense existing = expenseRepository.findByIdAndUserid(id, scope.ownerId())
                .orElseThrow(() -> new RuntimeException("Expense not found or you do not have permission to delete it"));
        expenseRepository.delete(existing);
    }

    private Specification<Expense> matchesFilters(UserDataScope scope, ExpenseFilterCriteria filters) {
        return (root, query, criteriaBuilder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(root.get("userid").in(scope.readableOwnerIds()));

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
