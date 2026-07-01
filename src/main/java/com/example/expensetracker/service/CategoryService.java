package com.example.expensetracker.service;

import com.example.expensetracker.model.ExpenseCategory;
import java.util.List;

public interface CategoryService {
    List<ExpenseCategory> getCategories(String userId, boolean includeInactive);
    ExpenseCategory createCategory(String userId, ExpenseCategory category);
    ExpenseCategory updateCategory(Long id, String userId, ExpenseCategory category);
    void deleteCategory(Long id, String userId);
}
