package com.example.expensetracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "receipt.processor.provider=azure",
        "receipt.processor.azure-function.url=http://localhost:7071/api/process-receipt",
        "management.endpoint.health.show-components=always"
})
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("dev")
class ObservabilityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void actuatorHealth_shouldBeAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void actuatorLiveness_shouldExcludeDatabaseHealth() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.livenessState.status").value("UP"))
                .andExpect(jsonPath("$.components.db").doesNotExist());
    }

    @Test
    void actuatorReadiness_shouldExcludeDatabaseHealth() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.readinessState.status").value("UP"))
                .andExpect(jsonPath("$.components.db").doesNotExist());
    }

    @Test
    void actuatorPrometheus_shouldBeAvailable() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("# HELP")));
    }

    @Test
    void requestWithCorrelationId_shouldEchoHeader() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header(CorrelationId.HEADER_NAME, "test-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationId.HEADER_NAME, "test-correlation-id"));
    }

    @Test
    void requestWithoutCorrelationId_shouldGenerateHeader() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationId.HEADER_NAME));
    }

    @Test
    void corsPreflight_shouldAllowCorrelationIdHeader() throws Exception {
        mockMvc.perform(options("/actuator/health")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", CorrelationId.HEADER_NAME))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", containsString(CorrelationId.HEADER_NAME)));
    }
}
