package com.example.expensetracker.repository;

import com.example.expensetracker.model.ExpenseCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {
    List<ExpenseCategory> findByUseridOrderByNameAsc(String userId);
    List<ExpenseCategory> findByUseridInOrderByNameAsc(List<String> userIds);
    List<ExpenseCategory> findByUseridAndActiveTrueOrderByNameAsc(String userId);
    List<ExpenseCategory> findByUseridInAndActiveTrueOrderByNameAsc(List<String> userIds);
    Optional<ExpenseCategory> findByIdAndUserid(Long id, String userId);
    Optional<ExpenseCategory> findByUseridAndNameIgnoreCase(String userId, String name);
}
