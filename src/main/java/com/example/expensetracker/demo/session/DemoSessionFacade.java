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

    public DemoSessionFacade(
        DemoDatabaseInitializer demoDatabaseInitializer,
        DataRealmExecutor realmExecutor,
        DemoSessionService demoSessionService,
        DemoMetrics metrics
    ) {
        this.demoDatabaseInitializer = demoDatabaseInitializer;
        this.realmExecutor = realmExecutor;
        this.demoSessionService = demoSessionService;
        this.metrics = metrics;
    }

    public DemoSessionService.SessionGrant createOrResume(String rawResumeCookie) {
        return createOrResume(rawResumeCookie, "unknown");
    }

    public DemoSessionService.SessionGrant createOrResume(String rawResumeCookie, String remoteAddress) {
        try {
            ensureMigrated();
            return realmExecutor.inRealm(DataRealm.DEMO,
                () -> demoSessionService.createOrResume(rawResumeCookie, remoteAddress));
        } catch (DemoSessionException exception) {
            if ("DEMO_CAPACITY_REACHED".equals(exception.code())) {
                metrics.capacityRejected();
            }
            throw exception;
        } catch (RuntimeException exception) {
            metrics.databaseFailure();
            throw DemoSessionException.serviceUnavailable(exception);
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
