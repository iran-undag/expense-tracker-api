package com.example.expensetracker.demo.session;

import com.example.expensetracker.demo.seed.DemoDatabaseInitializer;
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

    public DemoSessionFacade(
        DemoDatabaseInitializer demoDatabaseInitializer,
        DataRealmExecutor realmExecutor,
        DemoSessionService demoSessionService
    ) {
        this.demoDatabaseInitializer = demoDatabaseInitializer;
        this.realmExecutor = realmExecutor;
        this.demoSessionService = demoSessionService;
    }

    public DemoSessionService.SessionGrant createOrResume(String rawResumeCookie) {
        return createOrResume(rawResumeCookie, "unknown");
    }

    public DemoSessionService.SessionGrant createOrResume(String rawResumeCookie, String remoteAddress) {
        ensureMigrated();
        return realmExecutor.inRealm(DataRealm.DEMO,
            () -> demoSessionService.createOrResume(rawResumeCookie, remoteAddress));
    }

    public void logout(UUID sessionId) {
        ensureMigrated();
        realmExecutor.inRealm(DataRealm.DEMO, () -> demoSessionService.logout(sessionId));
    }

    private void ensureMigrated() {
        try {
            demoDatabaseInitializer.ensureMigrated();
        } catch (RuntimeException exception) {
            throw DemoSessionException.serviceUnavailable(exception);
        }
    }
}
