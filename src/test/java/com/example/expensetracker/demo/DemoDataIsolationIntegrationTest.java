package com.example.expensetracker.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.expensetracker.dto.BudgetMapper;
import com.example.expensetracker.dto.CategoryMapper;
import com.example.expensetracker.dto.ExpenseResponseDto;
import com.example.expensetracker.dto.RecurringExpenseMapper;
import com.example.expensetracker.model.Budget;
import com.example.expensetracker.model.Expense;
import com.example.expensetracker.model.ExpenseCategory;
import com.example.expensetracker.model.RecurringExpense;
import com.example.expensetracker.model.RecurringFrequency;
import com.example.expensetracker.repository.BudgetRepository;
import com.example.expensetracker.repository.ExpenseCategoryRepository;
import com.example.expensetracker.repository.ExpenseRepository;
import com.example.expensetracker.repository.RecurringExpenseOccurrenceRepository;
import com.example.expensetracker.repository.RecurringExpenseRepository;
import com.example.expensetracker.security.UserDataScope;
import com.example.expensetracker.service.BudgetService;
import com.example.expensetracker.service.CategoryService;
import com.example.expensetracker.service.ExpenseFilterCriteria;
import com.example.expensetracker.service.ExpenseService;
import com.example.expensetracker.service.RecurringExpenseService;
import com.example.expensetracker.service.ReportService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class DemoDataIsolationIntegrationTest {

    private static final String SEED_OWNER = "demo:seed";
    private static final UserDataScope SESSION_A = demoScope("demo:a");
    private static final UserDataScope SESSION_B = demoScope("demo:b");
    private static final UserDataScope PERSONAL = UserDataScope.personal("personal:user");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 14);

    @Autowired private ExpenseService expenseService;
    @Autowired private BudgetService budgetService;
    @Autowired private CategoryService categoryService;
    @Autowired private RecurringExpenseService recurringExpenseService;
    @Autowired private ReportService reportService;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private ExpenseCategoryRepository categoryRepository;
    @Autowired private RecurringExpenseRepository recurringExpenseRepository;
    @Autowired private RecurringExpenseOccurrenceRepository occurrenceRepository;

    @BeforeEach
    void setUp() {
        occurrenceRepository.deleteAll();
        recurringExpenseRepository.deleteAll();
        expenseRepository.deleteAll();
        budgetRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    void demoReadsIncludeSeedAndCurrentSessionButNotOtherSessions() {
        expenseRepository.saveAll(List.of(
            expense(SEED_OWNER, "Seed", "10", true),
            expense(SESSION_A.ownerId(), "Session A", "20", false),
            expense(SESSION_B.ownerId(), "Session B", "40", false),
            expense(PERSONAL.ownerId(), "Personal", "80", false)
        ));

        assertThat(expenseService.getAllExpenses(SESSION_A, new ExpenseFilterCriteria(null, null, null, null, null, null), PageRequest.of(0, 20)))
            .extracting(Expense::getDescription)
            .containsExactlyInAnyOrder("Seed", "Session A");
        assertThat(expenseService.getAllExpenses(PERSONAL, null, PageRequest.of(0, 20)))
            .extracting(Expense::getDescription)
            .containsExactly("Personal");
        assertThat(reportService.getMonthlySummary(SESSION_A, 2026, 8).getTotalAmount())
            .isEqualByComparingTo("30");
    }

    @Test
    void categoryOverlayUsesSessionRowAndInactiveOverlayStillHidesSeed() {
        categoryRepository.saveAll(List.of(
            category(SEED_OWNER, "Food", true, true),
            category(SEED_OWNER, "Travel", true, true),
            category(SESSION_A.ownerId(), " food ", true, false),
            category(SESSION_A.ownerId(), "TRAVEL", false, false),
            category(SESSION_B.ownerId(), "Food", true, false)
        ));

        assertThat(categoryService.getCategories(SESSION_A, false))
            .extracting(ExpenseCategory::getUserid, ExpenseCategory::getName)
            .containsExactly(org.assertj.core.groups.Tuple.tuple(SESSION_A.ownerId(), " food "));
        assertThat(categoryService.getCategories(SESSION_A, true))
            .extracting(ExpenseCategory::getUserid, ExpenseCategory::getName)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(SESSION_A.ownerId(), " food "),
                org.assertj.core.groups.Tuple.tuple(SESSION_A.ownerId(), "TRAVEL")
            );
    }

    @Test
    void budgetOverlayUsesSessionValueForSameMonthAndCategory() {
        budgetRepository.saveAll(List.of(
            budget(SEED_OWNER, "Food", "100", true),
            budget(SESSION_A.ownerId(), " food ", "250", false),
            budget(SESSION_B.ownerId(), "Food", "900", false)
        ));
        expenseRepository.save(expense(SEED_OWNER, "Seed food", "10", true));

        assertThat(budgetService.getBudgets(SESSION_A, 2026, 8))
            .singleElement()
            .satisfies(budget -> {
                assertThat(budget.getUserid()).isEqualTo(SESSION_A.ownerId());
                assertThat(budget.getAmount()).isEqualByComparingTo("250");
            });
        assertThat(budgetService.getBudgetSummary(SESSION_A, 2026, 8))
            .singleElement()
            .satisfies(summary -> {
                assertThat(summary.getBudgetAmount()).isEqualByComparingTo("250");
                assertThat(summary.getActualAmount()).isEqualByComparingTo("10");
            });
    }

    @Test
    void recurringGenerationUsesOnlySessionRulesAndPreservesSessionOwnership() {
        recurringExpenseRepository.saveAll(List.of(
            recurring(SEED_OWNER, "Seed rule", true),
            recurring(SESSION_A.ownerId(), "Session rule", false),
            recurring(SESSION_B.ownerId(), "Other rule", false)
        ));

        assertThat(recurringExpenseService.getRecurringExpenses(SESSION_A))
            .extracting(RecurringExpense::getDescription)
            .containsExactlyInAnyOrder("Seed rule", "Session rule");
        assertThat(recurringExpenseService.generateDueExpenses(SESSION_A, TODAY)).isEqualTo(1);
        assertThat(expenseRepository.findAll())
            .singleElement()
            .satisfies(expense -> {
                assertThat(expense.getUserid()).isEqualTo(SESSION_A.ownerId());
                assertThat(expense.getDemoSessionId()).isEqualTo(SESSION_A.demoSessionId());
                assertThat(expense.isDemoSeed()).isFalse();
            });
    }

    @Test
    void demoWritesCarrySessionMetadataAndSeedDtosAreProtected() {
        Expense saved = expenseService.saveExpense(SESSION_A, expense(null, "Created", "12", false));

        assertThat(saved.getUserid()).isEqualTo(SESSION_A.ownerId());
        assertThat(saved.getDemoSessionId()).isEqualTo(SESSION_A.demoSessionId());
        assertThat(saved.isDemoSeed()).isFalse();
        assertThat(ExpenseResponseDto.fromEntity(expense(SEED_OWNER, "Seed", "1", true)).isProtectedSeed()).isTrue();
        assertThat(BudgetMapper.toDto(budget(SEED_OWNER, "Food", "1", true)).isProtectedSeed()).isTrue();
        assertThat(CategoryMapper.toDto(category(SEED_OWNER, "Food", true, true)).isProtectedSeed()).isTrue();
        assertThat(RecurringExpenseMapper.toDto(recurring(SEED_OWNER, "Seed", true)).isProtectedSeed()).isTrue();
    }

    private static UserDataScope demoScope(String ownerId) {
        return new UserDataScope(ownerId, List.of(SEED_OWNER, ownerId), UUID.nameUUIDFromBytes(ownerId.getBytes()), true);
    }

    private Expense expense(String owner, String description, String amount, boolean seed) {
        return Expense.builder().userid(owner).description(description).amount(new BigDecimal(amount))
            .date(TODAY).category("Food").demoSeed(seed).build();
    }

    private ExpenseCategory category(String owner, String name, boolean active, boolean seed) {
        return ExpenseCategory.builder().userid(owner).name(name).active(active).systemDefault(seed).demoSeed(seed).build();
    }

    private Budget budget(String owner, String category, String amount, boolean seed) {
        return Budget.builder().userid(owner).category(category).budgetYear(2026).budgetMonth(8)
            .amount(new BigDecimal(amount)).demoSeed(seed).build();
    }

    private RecurringExpense recurring(String owner, String description, boolean seed) {
        return RecurringExpense.builder().userid(owner).description(description).amount(BigDecimal.ONE).category("Food")
            .frequency(RecurringFrequency.MONTHLY).startDate(TODAY).nextRunDate(TODAY).active(true)
            .demoSessionId(seed ? null : owner.equals(SESSION_A.ownerId()) ? SESSION_A.demoSessionId() : SESSION_B.demoSessionId())
            .demoSeed(seed).build();
    }
}
