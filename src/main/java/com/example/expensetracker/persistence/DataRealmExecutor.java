package com.example.expensetracker.persistence;

import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class DataRealmExecutor {
    public <T> T inRealm(DataRealm realm, Supplier<T> work) {
        Objects.requireNonNull(realm, "realm");
        Objects.requireNonNull(work, "work");

        boolean ownsScope = DataRealmContext.current().isEmpty();
        DataRealmContext.set(realm);
        try {
            return work.get();
        } finally {
            if (ownsScope) {
                DataRealmContext.clear();
            }
        }
    }

    public void inRealm(DataRealm realm, Runnable work) {
        inRealm(realm, () -> {
            work.run();
            return null;
        });
    }
}
