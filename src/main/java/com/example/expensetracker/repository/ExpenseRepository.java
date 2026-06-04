package com.example.expensetracker.repository;

import com.example.expensetracker.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByUserid(String userid);
    List<Expense> findByUseridAndDate(String userid, LocalDate date);
    List<Expense> findByUseridAndDateBetween(String userid, LocalDate startDate, LocalDate endDate);
    Optional<Expense> findByIdAndUserid(Long id, String userid);
}
