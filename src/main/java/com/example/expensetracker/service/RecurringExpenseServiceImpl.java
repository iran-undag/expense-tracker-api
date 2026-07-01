package com.example.expensetracker.service;

import com.example.expensetracker.model.Expense;
import com.example.expensetracker.model.RecurringExpense;
import com.example.expensetracker.model.RecurringExpenseOccurrence;
import com.example.expensetracker.repository.ExpenseRepository;
import com.example.expensetracker.repository.RecurringExpenseOccurrenceRepository;
import com.example.expensetracker.repository.RecurringExpenseRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecurringExpenseServiceImpl implements RecurringExpenseService {

    private final RecurringExpenseRepository recurringExpenseRepository;
    private final RecurringExpenseOccurrenceRepository occurrenceRepository;
    private final ExpenseRepository expenseRepository;

    @Override
    @Transactional
    public RecurringExpense saveRecurringExpense(String userId, RecurringExpense recurringExpense) {
        validate(recurringExpense);
        recurringExpense.setUserid(userId);
        recurringExpense.setDescription(normalizeOptional(recurringExpense.getDescription()));
        recurringExpense.setCategory(normalizeCategory(recurringExpense.getCategory()));
        recurringExpense.setNextRunDate(recurringExpense.getStartDate());
        return recurringExpenseRepository.save(recurringExpense);
    }

    @Override
    public List<RecurringExpense> getRecurringExpenses(String userId) {
        return recurringExpenseRepository.findByUseridOrderByActiveDescNextRunDateAsc(userId);
    }

    @Override
    @Transactional
    public RecurringExpense updateRecurringExpense(Long id, String userId, RecurringExpense recurringExpense) {
        validate(recurringExpense);
        RecurringExpense existing = recurringExpenseRepository.findByIdAndUserid(id, userId)
            .orElseThrow(() -> new RuntimeException("Recurring expense not found or you do not have permission to update it"));

        existing.setDescription(normalizeOptional(recurringExpense.getDescription()));
        existing.setAmount(recurringExpense.getAmount());
        existing.setCategory(normalizeCategory(recurringExpense.getCategory()));
        existing.setFrequency(recurringExpense.getFrequency());
        existing.setStartDate(recurringExpense.getStartDate());
        existing.setEndDate(recurringExpense.getEndDate());
        existing.setActive(recurringExpense.isActive());
        if (existing.getNextRunDate() == null || existing.getNextRunDate().isBefore(recurringExpense.getStartDate())) {
            existing.setNextRunDate(recurringExpense.getStartDate());
        }
        if (existing.getEndDate() != null && existing.getNextRunDate().isAfter(existing.getEndDate())) {
            existing.setActive(false);
        }
        return recurringExpenseRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteRecurringExpense(Long id, String userId) {
        RecurringExpense existing = recurringExpenseRepository.findByIdAndUserid(id, userId)
            .orElseThrow(() -> new RuntimeException("Recurring expense not found or you do not have permission to delete it"));
        recurringExpenseRepository.delete(existing);
    }

    @Override
    @Transactional
    public int generateDueExpenses(String userId, LocalDate today) {
        int generated = 0;
        for (RecurringExpense rule : recurringExpenseRepository.findByUseridAndActiveTrueAndNextRunDateLessThanEqual(userId, today)) {
            generated += generateForRule(rule, today);
        }
        return generated;
    }

    private int generateForRule(RecurringExpense rule, LocalDate today) {
        int generated = 0;
        LocalDate occurrenceDate = rule.getNextRunDate();
        while (occurrenceDate != null && !occurrenceDate.isAfter(today) && !isPastEndDate(rule, occurrenceDate)) {
            if (!occurrenceRepository.existsByRecurringExpenseIdAndOccurrenceDate(rule.getId(), occurrenceDate)) {
                Expense expense = expenseRepository.save(Expense.builder()
                    .userid(rule.getUserid())
                    .description(rule.getDescription())
                    .amount(rule.getAmount())
                    .date(occurrenceDate)
                    .category(rule.getCategory())
                    .build());
                occurrenceRepository.save(RecurringExpenseOccurrence.builder()
                    .recurringExpenseId(rule.getId())
                    .userid(rule.getUserid())
                    .occurrenceDate(occurrenceDate)
                    .expenseId(expense.getId())
                    .build());
                generated += 1;
            }
            occurrenceDate = nextDate(rule, occurrenceDate);
        }

        rule.setNextRunDate(occurrenceDate);
        if (occurrenceDate == null || isPastEndDate(rule, occurrenceDate)) {
            rule.setActive(false);
        }
        recurringExpenseRepository.save(rule);
        return generated;
    }

    private LocalDate nextDate(RecurringExpense rule, LocalDate current) {
        return switch (rule.getFrequency()) {
            case DAILY -> current.plusDays(1);
            case WEEKLY -> current.plusWeeks(1);
            case MONTHLY -> current.plusMonths(1);
            case YEARLY -> current.plusYears(1);
        };
    }

    private boolean isPastEndDate(RecurringExpense rule, LocalDate date) {
        return rule.getEndDate() != null && date.isAfter(rule.getEndDate());
    }

    private void validate(RecurringExpense recurringExpense) {
        if (recurringExpense.getAmount() == null || recurringExpense.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (recurringExpense.getFrequency() == null) {
            throw new IllegalArgumentException("Frequency is required");
        }
        if (recurringExpense.getStartDate() == null) {
            throw new IllegalArgumentException("Start date is required");
        }
        if (recurringExpense.getEndDate() != null && recurringExpense.getEndDate().isBefore(recurringExpense.getStartDate())) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
    }

    private String normalizeCategory(String value) {
        return value == null || value.isBlank() ? "Other" : value.trim();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
