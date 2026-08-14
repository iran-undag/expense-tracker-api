package com.example.expensetracker.demo.security;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DemoPrincipal(
        UUID sessionId,
        String sharedAccountId,
        String persistenceOwnerId,
        OffsetDateTime expiresAt) implements Principal {

    @Override
    public String getName() {
        return persistenceOwnerId;
    }
}
