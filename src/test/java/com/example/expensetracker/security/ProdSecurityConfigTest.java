package com.example.expensetracker.security;

import com.example.expensetracker.config.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProdSecurityProbeController.class)
@Import({ProdSecurityConfig.class, CorrelationIdFilter.class})
@ActiveProfiles("prod")
class ProdSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void allowsUnauthenticatedLivenessProbe() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
    }

    @Test
    void allowsUnauthenticatedReadinessProbe() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
    }

    @Test
    void protectsUnrelatedEndpoint() throws Exception {
        mockMvc.perform(get("/private-probe"))
                .andExpect(status().isUnauthorized());
    }
}

@RestController
class ProdSecurityProbeController {

    @GetMapping({"/actuator/health/liveness", "/actuator/health/readiness", "/private-probe"})
    ResponseEntity<Void> get() {
        return ResponseEntity.ok().build();
    }
}
