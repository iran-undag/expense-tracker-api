package com.example.expensetracker.repository;

import com.example.expensetracker.model.RecurringExpense;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, Long> {
    List<RecurringExpense> findByUseridOrderByActiveDescNextRunDateAsc(String userId);
    List<RecurringExpense> findByUseridAndActiveTrueAndNextRunDateLessThanEqual(String userId, LocalDate nextRunDate);
    Optional<RecurringExpense> findByIdAndUserid(Long id, String userId);
}
