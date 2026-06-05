package com.example.expensetracker.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class SecurityProfileGuard implements ApplicationRunner {

    private final Environment environment;

    public SecurityProfileGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean devActive = acceptsProfile("dev");
        boolean prodActive = acceptsProfile("prod");

        if (devActive == prodActive) {
            throw new IllegalStateException("Exactly one security profile must be active: dev or prod.");
        }
    }

    private boolean acceptsProfile(String profile) {
        return Arrays.asList(environment.getActiveProfiles()).contains(profile);
    }
}
