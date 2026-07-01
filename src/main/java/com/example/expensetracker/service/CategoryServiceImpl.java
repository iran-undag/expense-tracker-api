package com.example.expensetracker.service;

import com.example.expensetracker.model.ExpenseCategory;
import com.example.expensetracker.repository.ExpenseCategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private static final List<String> DEFAULT_CATEGORY_NAMES = List.of(
        "Food",
        "Groceries",
        "Transport",
        "Electricity",
        "Water",
        "Internet",
        "Phone",
        "Healthcare",
        "Shopping",
        "Travel",
        "Entertainment",
        "Other"
    );

    private static final List<String> DEFAULT_CATEGORY_COLORS = List.of(
        "#4f6bed",
        "#16a34a",
        "#0ea5e9",
        "#f59e0b",
        "#06b6d4",
        "#7c3aed",
        "#ec4899",
        "#dc2626",
        "#e6007e",
        "#8b6fcb",
        "#2aa7a5",
        "#64748b"
    );

    private final ExpenseCategoryRepository categoryRepository;

    @Override
    @Transactional
    public List<ExpenseCategory> getCategories(String userId, boolean includeInactive) {
        ensureDefaultCategories(userId);
        return includeInactive
            ? categoryRepository.findByUseridOrderByNameAsc(userId)
            : categoryRepository.findByUseridAndActiveTrueOrderByNameAsc(userId);
    }

    @Override
    @Transactional
    public ExpenseCategory createCategory(String userId, ExpenseCategory category) {
        String name = normalizeName(category.getName());
        return categoryRepository.findByUseridAndNameIgnoreCase(userId, name)
            .map(existing -> {
                if (existing.isActive()) {
                    throw new IllegalArgumentException("Category already exists");
                }
                existing.setActive(true);
                existing.setColor(normalizeOptional(category.getColor()));
                existing.setIcon(normalizeOptional(category.getIcon()));
                return categoryRepository.save(existing);
            })
            .orElseGet(() -> {
                category.setUserid(userId);
                category.setName(name);
                category.setColor(normalizeOptional(category.getColor()));
                category.setIcon(normalizeOptional(category.getIcon()));
                category.setSystemDefault(false);
                category.setActive(true);
                return categoryRepository.save(category);
            });
    }

    @Override
    @Transactional
    public ExpenseCategory updateCategory(Long id, String userId, ExpenseCategory category) {
        ExpenseCategory existing = categoryRepository.findByIdAndUserid(id, userId)
            .orElseThrow(() -> new RuntimeException("Category not found or you do not have permission to update it"));

        String name = normalizeName(category.getName());
        categoryRepository.findByUseridAndNameIgnoreCase(userId, name)
            .filter(match -> !match.getId().equals(id))
            .ifPresent(match -> {
                throw new IllegalArgumentException("Category already exists");
            });

        existing.setName(name);
        existing.setColor(normalizeOptional(category.getColor()));
        existing.setIcon(normalizeOptional(category.getIcon()));
        existing.setActive(category.isActive());
        return categoryRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id, String userId) {
        ExpenseCategory existing = categoryRepository.findByIdAndUserid(id, userId)
            .orElseThrow(() -> new RuntimeException("Category not found or you do not have permission to delete it"));
        existing.setActive(false);
        categoryRepository.save(existing);
    }

    private void ensureDefaultCategories(String userId) {
        if (categoryRepository.existsByUseridAndSystemDefaultTrue(userId)) {
            return;
        }

        for (int index = 0; index < DEFAULT_CATEGORY_NAMES.size(); index += 1) {
            String name = DEFAULT_CATEGORY_NAMES.get(index);
            if (categoryRepository.findByUseridAndNameIgnoreCase(userId, name).isPresent()) {
                continue;
            }
            categoryRepository.save(ExpenseCategory.builder()
                .userid(userId)
                .name(name)
                .color(DEFAULT_CATEGORY_COLORS.get(index))
                .icon(name.toLowerCase())
                .systemDefault(true)
                .active(true)
                .build());
        }
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name is required");
        }
        return name.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
