package com.example.expensetracker.demo.quota;

import com.example.expensetracker.demo.security.DemoPrincipal;
import com.example.expensetracker.demo.session.DemoSession;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoMutationExecutor {

    private final DemoQuotaService quotaService;

    public DemoMutationExecutor(DemoQuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @Transactional
    public <T> T execute(Authentication authentication, int cost, Supplier<T> mutation) {
        Objects.requireNonNull(mutation, "mutation");
        if (authentication == null || !(authentication.getPrincipal() instanceof DemoPrincipal demo)) {
            return mutation.get();
        }

        DemoSession session = quotaService.lockForMutation(demo.sessionId(), cost);
        T result = mutation.get();
        quotaService.recordUsed(session, cost);
        return result;
    }
}
