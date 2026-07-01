package com.example.expensetracker.repository;

import com.example.expensetracker.model.RecurringExpenseOccurrence;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecurringExpenseOccurrenceRepository extends JpaRepository<RecurringExpenseOccurrence, Long> {
    boolean existsByRecurringExpenseIdAndOccurrenceDate(Long recurringExpenseId, LocalDate occurrenceDate);
}
