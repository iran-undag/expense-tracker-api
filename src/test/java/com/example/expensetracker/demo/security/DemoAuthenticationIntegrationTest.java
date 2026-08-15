package com.example.expensetracker.demo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.expensetracker.config.CorrelationIdFilter;
import com.example.expensetracker.demo.quota.DemoQuotaService;
import com.example.expensetracker.demo.quota.DemoSessionHeadersAdvice;
import com.example.expensetracker.demo.session.DemoSessionController;
import com.example.expensetracker.demo.session.DemoSessionFacade;
import com.example.expensetracker.demo.session.DemoSessionResponse;
import com.example.expensetracker.demo.session.DemoSessionService;
import com.example.expensetracker.persistence.DataRealmContext;
import com.example.expensetracker.security.ProdSecurityConfig;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest({DemoAuthenticationProbeController.class, DemoSessionController.class})
@Import({
    ProdSecurityConfig.class,
    CorrelationIdFilter.class,
    DemoAuthenticationIntegrationTest.DemoJdbcConfiguration.class
})
@ActiveProfiles("prod")
@TestPropertySource(properties = "demo.token-hmac-key=0123456789abcdef0123456789abcdef")
public class DemoAuthenticationIntegrationTest {

    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String RAW_TOKEN = "dmo_example-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("demoJdbcTemplate")
    private JdbcTemplate demoJdbc;

    @Autowired
    private DemoTokenDigester digester;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private DemoSessionFacade demoSessionFacade;

    @MockBean
    private DemoQuotaService demoQuotaService;

    @BeforeEach
    void clearDemoRows() {
        demoJdbc.update("DELETE FROM demo_access_token");
        demoJdbc.update("DELETE FROM demo_session");
        when(demoQuotaService.current(SESSION_ID)).thenReturn(
            new DemoQuotaService.QuotaSnapshot(10, 10, OffsetDateTime.now(ZoneOffset.UTC).plusHours(1)));
    }

    @Test
    void authenticatesActiveDemoTokenByDigestAndSelectsDemoRealm() throws Exception {
        insertSession("ACTIVE", hoursFromNow(6));
        insertAccessToken(digester.digest(RAW_TOKEN), minutesFromNow(15));

        mockMvc.perform(get("/demo-auth-probe").header("Authorization", "Bearer " + RAW_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.principalType").value("DemoPrincipal"))
            .andExpect(jsonPath("$.principalName").value("demo-session-owner"))
            .andExpect(jsonPath("$.realm").value("DEMO"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string(DemoSessionHeadersAdvice.ACTIONS_LIMIT, "10"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string(DemoSessionHeadersAdvice.ACTIONS_REMAINING, "10"));

        assertThat(DataRealmContext.current()).isEmpty();
    }

    @Test
    void rejectsExpiredDemoAccessToken() throws Exception {
        insertSession("ACTIVE", hoursFromNow(6));
        insertAccessToken(digester.digest(RAW_TOKEN), minutesFromNow(-1));

        mockMvc.perform(get("/demo-auth-probe").header("Authorization", "Bearer " + RAW_TOKEN))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredDemoSession() throws Exception {
        insertSession("ACTIVE", hoursFromNow(-1));
        insertAccessToken(digester.digest(RAW_TOKEN), minutesFromNow(15));

        mockMvc.perform(get("/demo-auth-probe").header("Authorization", "Bearer " + RAW_TOKEN))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInactiveDemoSession() throws Exception {
        insertSession("LOGGED_OUT", hoursFromNow(6));
        insertAccessToken(digester.digest(RAW_TOKEN), minutesFromNow(15));

        mockMvc.perform(get("/demo-auth-probe").header("Authorization", "Bearer " + RAW_TOKEN))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void keepsEntraJwtAuthenticationOnThePrimaryRealm() throws Exception {
        Jwt entraJwt = Jwt.withTokenValue("entra-token")
            .header("alg", "none")
            .subject("entra-subject")
            .claim("oid", "entra-object-id")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
        when(jwtDecoder.decode("entra-token")).thenReturn(entraJwt);

        mockMvc.perform(get("/demo-auth-probe").header("Authorization", "Bearer entra-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.principalType").value("Jwt"))
            .andExpect(jsonPath("$.realm").value("PRIMARY"));

        assertThat(DataRealmContext.current()).isEmpty();
    }

    @Test
    void permitsOnlyTheDemoSessionCreationMethodWithoutAuthentication() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        when(demoSessionFacade.createOrResume(any())).thenReturn(
            new DemoSessionService.SessionGrant(
                new DemoSessionResponse("dmo_access", now.plusMinutes(15), now.plusHours(1), 10, 0, 10),
                "resume-token",
                3_600
            ));

        mockMvc.perform(post("/api/demo/sessions"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/demo/sessions"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedBearerHeadersStillUseTheResourceServerUnauthorizedResponse() throws Exception {
        mockMvc.perform(get("/demo-auth-probe")
                .header("Authorization", "Bearer first-token, Bearer second-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void clearsDemoRealmAfterDownstreamFailure() throws Exception {
        insertSession("ACTIVE", hoursFromNow(6));
        insertAccessToken(digester.digest(RAW_TOKEN), minutesFromNow(15));

        mockMvc.perform(get("/demo-auth-failure").header("Authorization", "Bearer " + RAW_TOKEN))
            .andExpect(status().isInternalServerError());
        assertThat(DataRealmContext.current()).isEmpty();
    }

    private void insertSession(String status, OffsetDateTime expiresAt) {
        demoJdbc.update("""
            INSERT INTO demo_session (id, shared_account_id, persistence_owner_id, expires_at, status)
            VALUES (?, ?, ?, ?, ?)
            """, SESSION_ID, "shared-demo-account", "demo-session-owner", expiresAt, status);
    }

    private void insertAccessToken(String digest, OffsetDateTime expiresAt) {
        demoJdbc.update("""
            INSERT INTO demo_access_token (demo_session_id, token_digest, expires_at)
            VALUES (?, ?, ?)
            """, SESSION_ID, digest, expiresAt);
    }

    private static OffsetDateTime minutesFromNow(long minutes) {
        return OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(minutes);
    }

    private static OffsetDateTime hoursFromNow(long hours) {
        return OffsetDateTime.now(ZoneOffset.UTC).plusHours(hours);
    }

    public static OffsetDateTime databaseNow() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    @TestConfiguration
    static class DemoJdbcConfiguration {

        @Bean(name = "demoJdbcTemplate")
        JdbcTemplate demoJdbcTemplate() {
            DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:demo-auth;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
                "sa",
                ""
            );
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("""
                CREATE ALIAS IF NOT EXISTS SYSDATETIMEOFFSET
                FOR 'com.example.expensetracker.demo.security.DemoAuthenticationIntegrationTest.databaseNow'
                """);
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS demo_session (
                    id UUID PRIMARY KEY,
                    shared_account_id VARCHAR(64) NOT NULL,
                    persistence_owner_id VARCHAR(96) NOT NULL,
                    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    status VARCHAR(24) NOT NULL
                )
                """);
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS demo_access_token (
                    demo_session_id UUID NOT NULL,
                    token_digest CHAR(64) NOT NULL UNIQUE,
                    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
            return jdbc;
        }
    }
}

@RestController
class DemoAuthenticationProbeController {

    @GetMapping("/demo-auth-probe")
    Map<String, String> authenticated(Authentication authentication) {
        return Map.of(
            "principalType", authentication.getPrincipal().getClass().getSimpleName(),
            "principalName", authentication.getName(),
            "realm", DataRealmContext.current().orElseThrow().name()
        );
    }

    @GetMapping("/demo-auth-failure")
    void fail() {
        throw new IllegalStateException("probe failure");
    }
}
