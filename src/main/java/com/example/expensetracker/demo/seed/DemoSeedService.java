package com.example.expensetracker.demo.seed;

import com.example.expensetracker.repository.BudgetRepository;
import com.example.expensetracker.repository.ExpenseCategoryRepository;
import com.example.expensetracker.repository.ExpenseRepository;
import com.example.expensetracker.repository.RecurringExpenseRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Date;
import java.time.YearMonth;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("prod")
public class DemoSeedService implements DemoSeedRefresher {

    private final DemoSeedTemplate template;
    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final RecurringExpenseRepository recurringRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public DemoSeedService(
        DemoSeedTemplate template,
        ExpenseRepository expenseRepository,
        BudgetRepository budgetRepository,
        ExpenseCategoryRepository categoryRepository,
        RecurringExpenseRepository recurringRepository
    ) {
        this.template = template;
        this.expenseRepository = expenseRepository;
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.recurringRepository = recurringRepository;
    }

    @Override
    @Transactional
    public void refreshIfStale(YearMonth anchorMonth) {
        SeedState state = seedState();
        if (state.templateVersion() == DemoSeedTemplate.VERSION
            && state.anchorMonth().equals(anchorMonth)) {
            return;
        }
        if (activeSessionCount() > 0) {
            return;
        }

        deleteSeedRows();
        DemoSeedTemplate.SeedData seedData = template.generate(anchorMonth);
        categoryRepository.saveAll(seedData.categories());
        budgetRepository.saveAll(seedData.budgets());
        expenseRepository.saveAll(seedData.expenses());
        recurringRepository.saveAll(seedData.recurringExpenses());
        entityManager.flush();
        entityManager.createNativeQuery("""
            UPDATE demo_seed_state
            SET template_version = :templateVersion,
                anchor_month = :anchorMonth,
                refreshed_at = SYSDATETIMEOFFSET()
            WHERE id = 1
            """)
            .setParameter("templateVersion", DemoSeedTemplate.VERSION)
            .setParameter("anchorMonth", anchorMonth.atDay(1))
            .executeUpdate();
    }

    private SeedState seedState() {
        List<Object[]> rows = entityManager.createNativeQuery("""
            SELECT template_version, anchor_month FROM demo_seed_state WHERE id = 1
            """).getResultList();
        if (rows.isEmpty()) {
            throw new IllegalStateException("Demo seed state is not initialized");
        }
        Object[] row = rows.get(0);
        return new SeedState(((Number) row[0]).intValue(), YearMonth.from(((Date) row[1]).toLocalDate()));
    }

    private int activeSessionCount() {
        return ((Number) entityManager.createNativeQuery("""
            SELECT COUNT(*) FROM demo_session
            WHERE status = 'ACTIVE' AND expires_at > SYSDATETIMEOFFSET()
            """).getSingleResult()).intValue();
    }

    private void deleteSeedRows() {
        execute("""
            DELETE occurrence
            FROM recurring_expense_occurrence occurrence
            JOIN recurring_expense recurring ON recurring.id = occurrence.recurring_expense_id
            WHERE recurring.is_demo_seed = 1
            """);
        execute("DELETE FROM recurring_expense WHERE is_demo_seed = 1");
        execute("DELETE FROM expense WHERE is_demo_seed = 1");
        execute("DELETE FROM budget WHERE is_demo_seed = 1");
        execute("DELETE FROM expense_category WHERE is_demo_seed = 1");
    }

    private void execute(String sql) {
        entityManager.createNativeQuery(sql).executeUpdate();
    }

    private record SeedState(int templateVersion, YearMonth anchorMonth) {
    }
}
