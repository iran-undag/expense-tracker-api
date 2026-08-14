package com.example.expensetracker.security;

import java.util.List;
import java.util.UUID;

public record UserDataScope(
    String ownerId,
    List<String> readableOwnerIds,
    UUID demoSessionId,
    boolean demo
) {
    public UserDataScope {
        readableOwnerIds = List.copyOf(readableOwnerIds);
    }

    public static UserDataScope personal(String ownerId) {
        return new UserDataScope(ownerId, List.of(ownerId), null, false);
    }
}
