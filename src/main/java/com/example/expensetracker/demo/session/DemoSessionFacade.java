package com.example.expensetracker.demo.session;

import com.example.expensetracker.demo.seed.DemoDatabaseInitializer;
import com.example.expensetracker.config.DemoMetrics;
import com.example.expensetracker.persistence.DataRealm;
import com.example.expensetracker.persistence.DataRealmExecutor;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class DemoSessionFacade {

    private final DemoDatabaseInitializer demoDatabaseInitializer;
    private final DataRealmExecutor realmExecutor;
    private final DemoSessionService demoSessionService;
    private final DemoMetrics metrics;
    private final DemoSessionCleanupScheduler cleanupScheduler;

    public DemoSessionFacade(
        DemoDatabaseInitializer demoDatabaseInitializer,
        DataRealmExecutor realmExecutor,
        DemoSessionService demoSessionService,
        DemoMetrics metrics,
        DemoSessionCleanupScheduler cleanupScheduler
    ) {
        this.demoDatabaseInitializer = demoDatabaseInitializer;
        this.realmExecutor = realmExecutor;
        this.demoSessionService = demoSessionService;
        this.metrics = metrics;
        this.cleanupScheduler = cleanupScheduler;
    }

    public DemoSessionService.SessionGrant createOrResume(String rawResumeCookie) {
        boolean databaseReady = false;
        try {
            ensureMigrated();
            databaseReady = true;
            return realmExecutor.inRealm(DataRealm.DEMO,
                () -> demoSessionService.createOrResume(rawResumeCookie));
        } catch (DemoSessionException exception) {
            if ("DEMO_CAPACITY_REACHED".equals(exception.code())) {
                metrics.capacityRejected();
            }
            throw exception;
        } catch (RuntimeException exception) {
            metrics.databaseFailure();
            throw DemoSessionException.serviceUnavailable(exception);
        } finally {
            if (databaseReady) {
                cleanupScheduler.schedule();
            }
        }
    }

    public DemoSessionService.SessionGrant renew(String rawResumeCookie) {
        boolean databaseReady = false;
        try {
            ensureMigrated();
            databaseReady = true;
            return realmExecutor.inRealm(DataRealm.DEMO,
                () -> demoSessionService.renew(rawResumeCookie));
        } catch (DemoSessionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            metrics.databaseFailure();
            throw DemoSessionException.serviceUnavailable(exception);
        } finally {
            if (databaseReady) {
                cleanupScheduler.schedule();
            }
        }
    }

    public void logout(UUID sessionId) {
        try {
            ensureMigrated();
            realmExecutor.inRealm(DataRealm.DEMO, () -> demoSessionService.logout(sessionId));
        } catch (DemoSessionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            metrics.databaseFailure();
            throw DemoSessionException.serviceUnavailable(exception);
        }
    }

    private void ensureMigrated() {
        demoDatabaseInitializer.ensureMigrated();
    }
}
