package com.example.expensetracker.demo.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.expensetracker.config.CorsConfig;
import com.example.expensetracker.demo.security.DemoPrincipal;
import com.example.expensetracker.exception.GlobalExceptionHandler;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@WebMvcTest(DemoSessionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, CorsConfig.class})
@ActiveProfiles("prod")
@TestPropertySource(properties = {
    "demo.resume-cookie.same-site=Lax",
    "app.cors.allowed-origin-patterns=https://app.example.com"
})
class DemoSessionControllerTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.of(2026, 8, 12, 0, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UrlBasedCorsConfigurationSource corsConfigurationSource;

    @MockBean
    private DemoSessionFacade facade;

    @Test
    void createsSessionWithNoStoreResponseAndSecureResumeCookie() throws Exception {
        when(facade.createOrResume(null, "203.0.113.8")).thenReturn(grant("resume-token", 21_600));

        mockMvc.perform(post("/api/demo/sessions").with(request -> {
                request.setRemoteAddr("203.0.113.8");
                return request;
            }))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(header().string(HttpHeaders.SET_COOKIE,
                org.hamcrest.Matchers.allOf(
                    org.hamcrest.Matchers.containsString("demo_resume=resume-token"),
                    org.hamcrest.Matchers.containsString("Path=/api/demo/sessions"),
                    org.hamcrest.Matchers.containsString("Max-Age=21600"),
                    org.hamcrest.Matchers.containsString("Secure"),
                    org.hamcrest.Matchers.containsString("HttpOnly"),
                    org.hamcrest.Matchers.containsString("SameSite=Lax")
                )))
            .andExpect(jsonPath("$.accessToken").value("dmo_access-token"))
            .andExpect(jsonPath("$.actionLimit").value(20))
            .andExpect(jsonPath("$.usedActions").value(3))
            .andExpect(jsonPath("$.remainingActions").value(17));
    }

    @Test
    void passesOnlyTheResumeCookieAndServletRemoteAddressToTheFacade() throws Exception {
        when(facade.createOrResume("resume-token", "2001:db8:0:0:0:0:0:1"))
            .thenReturn(grant("renewed-resume-token", 10_800));

        mockMvc.perform(post("/api/demo/sessions")
                .cookie(new jakarta.servlet.http.Cookie("demo_resume", "resume-token"))
                .header("X-Forwarded-For", "198.51.100.4")
                .with(request -> {
                    request.setRemoteAddr("2001:db8:0:0:0:0:0:1");
                    return request;
                }))
            .andExpect(status().isOk());

        verify(facade).createOrResume("resume-token", "2001:db8:0:0:0:0:0:1");
    }

    @Test
    void returnsStableCapacityErrorAndRetryAfter() throws Exception {
        when(facade.createOrResume(any(), any()))
            .thenThrow(DemoSessionException.capacityReached(73));

        mockMvc.perform(post("/api/demo/sessions"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string(HttpHeaders.RETRY_AFTER, "73"))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.code").value("DEMO_CAPACITY_REACHED"));
    }

    @Test
    void returnsStableExpiredAndUnavailableErrors() throws Exception {
        when(facade.createOrResume("expired", "127.0.0.1"))
            .thenThrow(DemoSessionException.sessionExpired());
        when(facade.createOrResume("unavailable", "127.0.0.1"))
            .thenThrow(DemoSessionException.serviceUnavailable(new IllegalStateException("database")));

        mockMvc.perform(post("/api/demo/sessions")
                .cookie(new jakarta.servlet.http.Cookie("demo_resume", "expired")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("DEMO_SESSION_EXPIRED"));

        mockMvc.perform(post("/api/demo/sessions")
                .cookie(new jakarta.servlet.http.Cookie("demo_resume", "unavailable")))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("DEMO_SERVICE_UNAVAILABLE"));
    }

    @Test
    void logoutClearsCookieOnlyAfterFacadeReturns() throws Exception {
        UUID sessionId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        DemoPrincipal principal = new DemoPrincipal(sessionId, "demo-shared-account", "demo:" + sessionId,
            NOW.plusHours(6));

        mockMvc.perform(delete("/api/demo/sessions/current")
                .principal(new TestingAuthenticationToken(principal, null)))
            .andExpect(status().isNoContent())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(header().string(HttpHeaders.SET_COOKIE,
                org.hamcrest.Matchers.allOf(
                    org.hamcrest.Matchers.containsString("demo_resume="),
                    org.hamcrest.Matchers.containsString("Path=/api/demo/sessions"),
                    org.hamcrest.Matchers.containsString("Max-Age=0"),
                    org.hamcrest.Matchers.containsString("Secure"),
                    org.hamcrest.Matchers.containsString("HttpOnly"),
                    org.hamcrest.Matchers.containsString("SameSite=Lax")
                )));

        verify(facade).logout(sessionId);
    }

    @Test
    void corsAllowsConfiguredFrontendCredentialsAndExposesRetryAfter() {
        var configuration = corsConfigurationSource.getCorsConfigurations().get("/**");

        org.assertj.core.api.Assertions.assertThat(configuration.getAllowCredentials()).isTrue();
        org.assertj.core.api.Assertions.assertThat(configuration.getAllowedOriginPatterns())
            .containsExactly("https://app.example.com");
        org.assertj.core.api.Assertions.assertThat(configuration.getExposedHeaders())
            .contains(HttpHeaders.RETRY_AFTER);
    }

    private static DemoSessionService.SessionGrant grant(String resumeToken, long maxAge) {
        return new DemoSessionService.SessionGrant(
            new DemoSessionResponse(
                "dmo_access-token",
                NOW.plusMinutes(15),
                NOW.plusHours(6),
                20,
                3,
                17
            ),
            resumeToken,
            maxAge
        );
    }
}
