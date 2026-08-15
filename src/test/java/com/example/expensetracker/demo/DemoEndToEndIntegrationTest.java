package com.example.expensetracker.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.lifecycle.Startables;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class DemoEndToEndIntegrationTest {

    private static final MSSQLServerContainer<?> PRIMARY = sqlServer();
    private static final MSSQLServerContainer<?> DEMO = sqlServer();

    static {
        Startables.deepStart(Stream.of(PRIMARY, DEMO)).join();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PRIMARY::getJdbcUrl);
        registry.add("spring.datasource.username", PRIMARY::getUsername);
        registry.add("spring.datasource.password", PRIMARY::getPassword);
        registry.add("demo.datasource.url", DEMO::getJdbcUrl);
        registry.add("demo.datasource.username", DEMO::getUsername);
        registry.add("demo.datasource.password", DEMO::getPassword);
        registry.add("demo.token-hmac-key", () -> "0123456789abcdef0123456789abcdef");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () -> "http://localhost/unused-jwks");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private JwtDecoder jwtDecoder;

    @AfterAll
    static void stopContainers() {
        PRIMARY.stop();
        DEMO.stop();
    }

    @Test
    void isolatesRealmsEnforcesCapacityAndQuotaAndReleasesSlotOnLogout() throws Exception {
        mockMvc.perform(post("/api/expenses")
                .with(jwt().jwt(token -> token.subject("personal-subject")
                    .claim("oid", "personal-owner")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(expenseJson("Personal only")))
            .andExpect(status().isOk());

        String firstToken = createDemoSession("198.51.100.91");
        String secondToken = createDemoSession("198.51.100.92");

        mockMvc.perform(get("/api/expenses")
                .header("Authorization", "Bearer " + firstToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].description")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Personal only"))));

        mockMvc.perform(get("/api/expenses")
                .with(jwt().jwt(token -> token.subject("personal-subject")
                    .claim("oid", "personal-owner"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].description")
                .value(org.hamcrest.Matchers.hasItem("Personal only")));

        mockMvc.perform(post("/api/demo/sessions").with(request -> {
                request.setRemoteAddr("198.51.100.93");
                return request;
            }))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("DEMO_CAPACITY_REACHED"));

        for (int action = 1; action <= 10; action++) {
            mockMvc.perform(post("/api/expenses")
                    .header("Authorization", "Bearer " + firstToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(expenseJson("Demo action " + action)))
                .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/expenses")
                .header("Authorization", "Bearer " + firstToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(expenseJson("Rejected action")))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("DEMO_QUOTA_EXHAUSTED"));

        mockMvc.perform(delete("/api/demo/sessions/current")
                .header("Authorization", "Bearer " + firstToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/expenses")
                .header("Authorization", "Bearer " + firstToken))
            .andExpect(status().isUnauthorized());

        String replacementToken = createDemoSession("198.51.100.94");
        assertThat(replacementToken).startsWith("dmo_").isNotEqualTo(secondToken);
    }

    private String createDemoSession(String remoteAddress) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/demo/sessions").with(request -> {
                request.setRemoteAddr(remoteAddress);
                return request;
            }))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.actionLimit").value(10))
            .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("accessToken").asText();
    }

    private String expenseJson(String description) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
            "description", description,
            "amount", 1,
            "date", "2026-08-15",
            "category", "Other"
        ));
    }

    private static MSSQLServerContainer<?> sqlServer() {
        return new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
            .acceptLicense();
    }
}
