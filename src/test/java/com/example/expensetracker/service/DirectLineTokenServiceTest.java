package com.example.expensetracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.time.Instant;
import com.example.expensetracker.security.UserDataScope;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.LoggerFactory;

@ExtendWith(OutputCaptureExtension.class)
class DirectLineTokenServiceTest {

    private DirectLineTokenService service;
    private ChatIdentityMappingService mappingService;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        mappingService = mock(ChatIdentityMappingService.class);
        service = new DirectLineTokenService(restTemplate, mappingService, new ObjectMapper());
        ReflectionTestUtils.setField(service, "directLineSecret", "direct-line-secret");
        ReflectionTestUtils.setField(
            service,
            "tokenUrl",
            "https://directline.example.test/v3/directline/tokens/generate"
        );
        ReflectionTestUtils.setField(
            service,
            "trustedOrigins",
            "http://localhost:5173,https://expense.example.test"
        );
    }

    @Test
    void issueToken_exchangesSecretAndStoresConversationBoundMapping() {
        server.expect(once(), requestTo("https://directline.example.test/v3/directline/tokens/generate"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer direct-line-secret"))
            .andExpect(jsonPath("$.user.id", startsWith("dl_")))
            .andExpect(jsonPath("$.user.name").value("Juan"))
            .andExpect(jsonPath("$.trustedOrigins[0]").value("http://localhost:5173"))
            .andExpect(jsonPath("$.trustedOrigins[1]").value("https://expense.example.test"))
            .andRespond(withSuccess("""
                {
                  "conversationId": "conversation-123",
                  "token": "short-lived-token",
                  "expires_in": 1800
                }
                """, MediaType.APPLICATION_JSON));

        Instant before = Instant.now();
        var response = service.issueToken("expense-owner-id", "Juan");
        Instant after = Instant.now();

        assertThat(response.getToken()).isEqualTo("short-lived-token");
        assertThat(response.getConversationId()).isEqualTo("conversation-123");
        assertThat(response.getExpiresInSeconds()).isEqualTo(1800);
        assertThat(response.getUserId()).startsWith("dl_");

        ArgumentCaptor<Instant> expiry = ArgumentCaptor.forClass(Instant.class);
        verify(mappingService).createMapping(
            org.mockito.ArgumentMatchers.eq(response.getUserId()),
            org.mockito.ArgumentMatchers.eq("conversation-123"),
            org.mockito.ArgumentMatchers.eq(UserDataScope.personal("expense-owner-id")),
            expiry.capture()
        );
        assertThat(expiry.getValue())
            .isBetween(before.plusSeconds(1800), after.plusSeconds(1800));
        server.verify();
    }

    @Test
    void issueToken_usesDemoPrefixAndStoresDemoScope() {
        UserDataScope scope = new UserDataScope(
            "demo:owner", List.of("demo:seed", "demo:owner"), UUID.randomUUID(), true);
        server.expect(once(), requestTo("https://directline.example.test/v3/directline/tokens/generate"))
            .andExpect(jsonPath("$.user.id", startsWith("dl_demo_")))
            .andRespond(withSuccess("""
                {"conversationId":"demo-conversation","token":"demo-token","expires_in":1800}
                """, MediaType.APPLICATION_JSON));

        var response = service.issueToken(scope, null);

        assertThat(response.getUserId()).startsWith("dl_demo_");
        verify(mappingService).createMapping(
            org.mockito.ArgumentMatchers.eq(response.getUserId()),
            org.mockito.ArgumentMatchers.eq("demo-conversation"),
            org.mockito.ArgumentMatchers.eq(scope),
            org.mockito.ArgumentMatchers.any(Instant.class));
        server.verify();
    }

    @Test
    void issueToken_omitsUserNameWhenFirstNameIsUnavailable() {
        server.expect(once(), requestTo("https://directline.example.test/v3/directline/tokens/generate"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.user.id", startsWith("dl_")))
            .andExpect(jsonPath("$.user.name").doesNotExist())
            .andRespond(withSuccess("""
                {
                  "conversationId": "conversation-123",
                  "token": "short-lived-token",
                  "expires_in": 1800
                }
                """, MediaType.APPLICATION_JSON));

        service.issueToken("expense-owner-id", null);

        server.verify();
    }

    @Test
    void issueToken_doesNotLogFirstName(CapturedOutput output) {
        server.expect(once(), requestTo("https://directline.example.test/v3/directline/tokens/generate"))
            .andRespond(withSuccess("""
                {
                  "conversationId": "conversation-123",
                  "token": "short-lived-token",
                  "expires_in": 1800
                }
                """, MediaType.APPLICATION_JSON));

        Logger restTemplateLogger = (Logger) LoggerFactory.getLogger(RestTemplate.class);
        Level previousLevel = restTemplateLogger.getLevel();
        try {
            restTemplateLogger.setLevel(Level.DEBUG);
            service.issueToken("expense-owner-id", "SensitiveFirstName");
        } finally {
            restTemplateLogger.setLevel(previousLevel);
        }

        assertThat(output).doesNotContain("SensitiveFirstName");
        server.verify();
    }

    @Test
    void issueToken_failsClosedWhenDirectLineIsNotConfigured() {
        ReflectionTestUtils.setField(service, "directLineSecret", "");

        assertThatThrownBy(() -> service.issueToken("expense-owner-id", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Azure Bot Direct Line is not configured");
    }

    @Test
    void issueToken_preservesSafeDirectLineErrorCodeForDiagnostics() {
        server.expect(once(), requestTo("https://directline.example.test/v3/directline/tokens/generate"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                    {
                      "code": "RegionNotAllowed",
                      "message": "Provider diagnostic text"
                    }
                    """));

        assertThatThrownBy(() -> service.issueToken("expense-owner-id", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Direct Line error code: RegionNotAllowed");
        server.verify();
    }
}
