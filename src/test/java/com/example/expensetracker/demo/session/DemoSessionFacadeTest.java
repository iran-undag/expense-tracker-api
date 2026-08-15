package com.example.expensetracker.demo.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.expensetracker.config.DemoMetrics;
import com.example.expensetracker.demo.seed.DemoDatabaseInitializer;
import com.example.expensetracker.persistence.DataRealm;
import com.example.expensetracker.persistence.DataRealmExecutor;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DemoSessionFacadeTest {

    private DemoDatabaseInitializer initializer;
    private DataRealmExecutor realmExecutor;
    private DemoSessionService sessionService;
    private DemoMetrics metrics;
    private DemoSessionCleanupScheduler cleanupScheduler;
    private DemoSessionFacade facade;

    @BeforeEach
    void setUp() {
        initializer = mock(DemoDatabaseInitializer.class);
        realmExecutor = mock(DataRealmExecutor.class);
        sessionService = mock(DemoSessionService.class);
        metrics = mock(DemoMetrics.class);
        cleanupScheduler = mock(DemoSessionCleanupScheduler.class);
        facade = new DemoSessionFacade(
            initializer, realmExecutor, sessionService, metrics, cleanupScheduler);
    }

    @Test
    void schedulesCleanupAfterSuccessfulDatabaseBackedLogin() {
        DemoSessionService.SessionGrant grant = mock(DemoSessionService.SessionGrant.class);
        when(realmExecutor.inRealm(
            eq(DataRealm.DEMO), anySessionGrantSupplier())).thenReturn(grant);

        assertThat(facade.createOrResume(null, "203.0.113.8")).isSameAs(grant);

        verify(cleanupScheduler).schedule();
    }

    @Test
    void schedulesCleanupAfterDatabaseBackedLoginRejection() {
        when(realmExecutor.inRealm(
            eq(DataRealm.DEMO), anySessionGrantSupplier()))
            .thenThrow(DemoSessionException.sessionExpired());

        assertThatThrownBy(() -> facade.createOrResume("expired", "203.0.113.8"))
            .isInstanceOfSatisfying(DemoSessionException.class,
                exception -> assertThat(exception.code()).isEqualTo("DEMO_SESSION_EXPIRED"));

        verify(cleanupScheduler).schedule();
    }

    @Test
    void doesNotScheduleCleanupWhenDatabaseInitializationFails() {
        doThrow(new IllegalStateException("database unavailable"))
            .when(initializer).ensureMigrated();

        assertThatThrownBy(() -> facade.createOrResume(null, "203.0.113.8"))
            .isInstanceOfSatisfying(DemoSessionException.class,
                exception -> assertThat(exception.code()).isEqualTo("DEMO_SERVICE_UNAVAILABLE"));

        verify(cleanupScheduler, never()).schedule();
    }

    @SuppressWarnings("unchecked")
    private static Supplier<DemoSessionService.SessionGrant> anySessionGrantSupplier() {
        return any(Supplier.class);
    }
}
