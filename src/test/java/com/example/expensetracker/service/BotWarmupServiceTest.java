package com.example.expensetracker.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BotWarmupServiceTest {
    private static final String URL = "https://chatbot.example.test/internal/warmup";
    private static final String KEY = "01234567890123456789012345678901";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void callsChatbotOncePerUserDuringCooldown() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo(URL)).andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Chatbot-Warmup-Key", KEY))
                .andRespond(withSuccess("{\"status\":\"ready\"}", MediaType.APPLICATION_JSON));
        BotWarmupService service = new BotWarmupService(restTemplate, URL, KEY, Duration.ofMinutes(5), CLOCK);

        assertThat(service.warmup("user-1")).isEqualTo(BotWarmupStatus.READY);
        assertThat(service.warmup("user-1")).isEqualTo(BotWarmupStatus.READY);

        server.verify();
    }

    @Test
    void mapsDownstreamFailureToDelayedWithoutThrowing() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(URL)).andRespond(withServerError());
        BotWarmupService service = new BotWarmupService(restTemplate, URL, KEY, Duration.ofMinutes(5), CLOCK);

        assertThat(service.warmup("user-2")).isEqualTo(BotWarmupStatus.DELAYED);

        server.verify();
    }
}
