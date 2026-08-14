package com.example.expensetracker.service;

import com.example.expensetracker.model.ExpenseCategory;
import com.example.expensetracker.security.UserDataScope;
import java.util.List;

public interface CategoryService {
    List<ExpenseCategory> getCategories(UserDataScope scope, boolean includeInactive);
    ExpenseCategory createCategory(UserDataScope scope, ExpenseCategory category);
    ExpenseCategory updateCategory(Long id, UserDataScope scope, ExpenseCategory category);
    void deleteCategory(Long id, UserDataScope scope);

    default List<ExpenseCategory> getCategories(String userId, boolean includeInactive) { return getCategories(UserDataScope.personal(userId), includeInactive); }
    default ExpenseCategory createCategory(String userId, ExpenseCategory category) { return createCategory(UserDataScope.personal(userId), category); }
    default ExpenseCategory updateCategory(Long id, String userId, ExpenseCategory category) { return updateCategory(id, UserDataScope.personal(userId), category); }
    default void deleteCategory(Long id, String userId) { deleteCategory(id, UserDataScope.personal(userId)); }
}
