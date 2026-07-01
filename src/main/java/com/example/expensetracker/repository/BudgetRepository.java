package com.example.expensetracker.repository;

import com.example.expensetracker.model.Budget;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findByUseridOrderByBudgetYearAscBudgetMonthAscCategoryAsc(String userId);
    List<Budget> findByUseridAndBudgetYearAndBudgetMonthOrderByCategoryAsc(String userId, Integer year, Integer month);
    Optional<Budget> findByIdAndUserid(Long id, String userId);
    Optional<Budget> findByUseridAndBudgetYearAndBudgetMonthAndCategoryIgnoreCase(String userId, Integer year, Integer month, String category);
}
