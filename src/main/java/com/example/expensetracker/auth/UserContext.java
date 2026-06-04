package com.example.expensetracker.auth;

import java.util.Optional;

public final class UserContext {

    private static final ThreadLocal<AuthenticatedUser> CURRENT_USER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(AuthenticatedUser user) {
        CURRENT_USER.set(user);
    }

    public static Optional<AuthenticatedUser> get() {
        return Optional.ofNullable(CURRENT_USER.get());
    }

    public static AuthenticatedUser requireCurrentUser() {
        return get().orElseThrow(() -> new IllegalStateException("Authenticated user context is missing"));
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
