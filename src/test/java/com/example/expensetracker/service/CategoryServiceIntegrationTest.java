package com.example.expensetracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.expensetracker.model.ExpenseCategory;
import com.example.expensetracker.repository.ExpenseCategoryRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class CategoryServiceIntegrationTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ExpenseCategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        categoryRepository.deleteAll();
    }

    @Test
    void getCategories_shouldCreateDefaultsForUserOnly() {
        List<ExpenseCategory> categories = categoryService.getCategories("testuser", false);

        assertThat(categories).extracting(ExpenseCategory::getName)
            .contains(
                "Food", "Groceries", "Transport", "Electricity", "Water", "Internet", "Phone",
                "Mortgage", "Rent", "Insurance", "Tuition", "Other")
            .doesNotContain("Utilities");
        assertThat(categories).allMatch(category -> "testuser".equals(category.getUserid()));
        assertThat(categoryRepository.findByUseridOrderByNameAsc("otheruser")).isEmpty();
    }

    @Test
    void getCategories_shouldReconcileMissingDefaultsForExistingUser() {
        categoryRepository.save(ExpenseCategory.builder()
            .userid("testuser")
            .name("Food")
            .color("#4f6bed")
            .icon("food")
            .systemDefault(true)
            .active(true)
            .build());

        List<ExpenseCategory> categories = categoryService.getCategories("testuser", true);

        assertThat(categories).extracting(ExpenseCategory::getName)
            .contains("Food", "Mortgage", "Rent", "Insurance", "Tuition");
        assertThat(categories).filteredOn(category -> "Food".equals(category.getName())).hasSize(1);
    }

    @Test
    void getCategories_shouldPreserveMatchingUserCategoryWithoutDuplicateOrReactivation() {
        ExpenseCategory rent = categoryRepository.save(ExpenseCategory.builder()
            .userid("testuser")
            .name("rent")
            .color("#123456")
            .icon("custom-home")
            .systemDefault(false)
            .active(false)
            .build());

        categoryService.getCategories("testuser", true);

        List<ExpenseCategory> matches = categoryRepository.findByUseridOrderByNameAsc("testuser").stream()
            .filter(category -> "rent".equalsIgnoreCase(category.getName()))
            .toList();
        assertThat(matches).singleElement().satisfies(saved -> {
            assertThat(saved.getId()).isEqualTo(rent.getId());
            assertThat(saved.getColor()).isEqualTo("#123456");
            assertThat(saved.getIcon()).isEqualTo("custom-home");
            assertThat(saved.isSystemDefault()).isFalse();
            assertThat(saved.isActive()).isFalse();
        });
    }

    @Test
    void createCategory_shouldRejectDuplicateActiveNameIgnoringCase() {
        categoryService.createCategory("testuser", category("Pets"));

        assertThatThrownBy(() -> categoryService.createCategory("testuser", category(" pets ")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Category already exists");
    }

    @Test
    void createCategory_shouldReactivateDeletedCategory() {
        ExpenseCategory created = categoryService.createCategory("testuser", category("Pets"));
        categoryService.deleteCategory(created.getId(), "testuser");

        ExpenseCategory restored = categoryService.createCategory("testuser", category("Pets"));

        assertThat(restored.getId()).isEqualTo(created.getId());
        assertThat(restored.isActive()).isTrue();
        assertThat(categoryRepository.findByUseridOrderByNameAsc("testuser")).hasSize(1);
    }

    @Test
    void updateCategory_shouldRejectDuplicateNameForSameUserOnly() {
        ExpenseCategory pets = categoryService.createCategory("testuser", category("Pets"));
        categoryService.createCategory("testuser", category("Bills"));
        categoryService.createCategory("otheruser", category("Home"));

        assertThatThrownBy(() -> categoryService.updateCategory(pets.getId(), "testuser", category("Bills")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Category already exists");

        ExpenseCategory updated = categoryService.updateCategory(pets.getId(), "testuser", category("Home"));
        assertThat(updated.getName()).isEqualTo("Home");
    }

    @Test
    void deleteCategory_shouldSoftDelete() {
        ExpenseCategory pets = categoryService.createCategory("testuser", category("Pets"));

        categoryService.deleteCategory(pets.getId(), "testuser");

        assertThat(categoryService.getCategories("testuser", false))
            .extracting(ExpenseCategory::getName)
            .doesNotContain("Pets");
        assertThat(categoryService.getCategories("testuser", true))
            .filteredOn(category -> "Pets".equals(category.getName()))
            .singleElement()
            .matches(category -> !category.isActive());
    }

    private ExpenseCategory category(String name) {
        return ExpenseCategory.builder()
            .name(name)
            .color("#123456")
            .icon("tag")
            .active(true)
            .build();
    }
}
