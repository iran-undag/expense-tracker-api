package com.example.expensetracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

class AzureSpeechTokenServiceTest {

    private AzureSpeechTokenService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new AzureSpeechTokenService(restTemplate);
        ReflectionTestUtils.setField(service, "speechKey", "test-key");
        ReflectionTestUtils.setField(service, "speechRegion", "southeastasia");
    }

    @Test
    void issueToken_shouldExchangeConfiguredKeyForShortLivedToken() {
        server.expect(once(), requestTo("https://southeastasia.api.cognitive.microsoft.com/sts/v1.0/issueToken"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Ocp-Apim-Subscription-Key", "test-key"))
            .andRespond(withSuccess("token-value", org.springframework.http.MediaType.TEXT_PLAIN));

        var token = service.issueToken();

        assertThat(token.getToken()).isEqualTo("token-value");
        assertThat(token.getRegion()).isEqualTo("southeastasia");
        assertThat(token.getExpiresInSeconds()).isEqualTo(540);
        server.verify();
    }

    @Test
    void issueToken_shouldUseConfiguredTokenUrlWhenProvided() {
        ReflectionTestUtils.setField(service, "speechTokenUrl", "https://speech.example.test/token");
        server.expect(once(), requestTo("https://speech.example.test/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Ocp-Apim-Subscription-Key", "test-key"))
            .andRespond(withSuccess("token-value", org.springframework.http.MediaType.TEXT_PLAIN));

        var token = service.issueToken();

        assertThat(token.getToken()).isEqualTo("token-value");
        assertThat(token.getRegion()).isEqualTo("southeastasia");
        server.verify();
    }

    @Test
    void issueToken_shouldFailWhenSpeechIsNotConfigured() {
        ReflectionTestUtils.setField(service, "speechKey", "");

        assertThatThrownBy(() -> service.issueToken())
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Azure Speech is not configured");
    }
}
