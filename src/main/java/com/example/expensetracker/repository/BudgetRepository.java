package com.example.expensetracker.repository;

import com.example.expensetracker.model.Budget;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findByUseridOrderByBudgetYearAscBudgetMonthAscCategoryAsc(String userId);
    List<Budget> findByUseridInOrderByBudgetYearAscBudgetMonthAscCategoryAsc(List<String> userIds);
    List<Budget> findByUseridAndBudgetYearAndBudgetMonthOrderByCategoryAsc(String userId, Integer year, Integer month);
    List<Budget> findByUseridInAndBudgetYearAndBudgetMonthOrderByCategoryAsc(
        List<String> userIds, Integer year, Integer month);
    @Query("""
        select b from Budget b
        where b.userid = :userId
          and (b.budgetYear < :year or (b.budgetYear = :year and b.budgetMonth <= :month))
        order by b.budgetYear asc, b.budgetMonth asc, b.category asc
        """)
    List<Budget> findEffectiveCandidates(
        @Param("userId") String userId,
        @Param("year") Integer year,
        @Param("month") Integer month
    );
    @Query("""
        select b from Budget b
        where b.userid in :userIds
          and (b.budgetYear < :year or (b.budgetYear = :year and b.budgetMonth <= :month))
        order by b.budgetYear asc, b.budgetMonth asc, b.category asc
        """)
    List<Budget> findEffectiveCandidatesForOwners(
        @Param("userIds") List<String> userIds,
        @Param("year") Integer year,
        @Param("month") Integer month
    );
    Optional<Budget> findByIdAndUserid(Long id, String userId);
    Optional<Budget> findByUseridAndBudgetYearAndBudgetMonthAndCategoryIgnoreCase(String userId, Integer year, Integer month, String category);
}
