package com.example.expensetracker.persistence;

import java.util.Optional;

public final class DataRealmContext {
    private static final ThreadLocal<DataRealm> CURRENT = new ThreadLocal<>();

    private DataRealmContext() {
    }

    public static Optional<DataRealm> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    static void set(DataRealm realm) {
        DataRealm current = CURRENT.get();
        if (current != null && current != realm) {
            throw new IllegalStateException("Cannot change data realm inside an active realm scope");
        }
        CURRENT.set(realm);
    }

    static void clear() {
        CURRENT.remove();
    }
}
