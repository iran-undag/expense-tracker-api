package com.example.expensetracker.demo.security;

import com.example.expensetracker.demo.session.DemoSessionException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class DemoFeatureGuard {

    public void requirePersonal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof DemoPrincipal) {
            throw DemoSessionException.featureDisabled();
        }
    }
}
