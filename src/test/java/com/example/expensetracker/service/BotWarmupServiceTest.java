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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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

    @Test
    void retriesDelayedWarmupFiveSecondsAfterCompletionThenCachesReadiness() {
        AtomicReference<Instant> now = new AtomicReference<>(CLOCK.instant());
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(ignored -> now.get());
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(URL)).andRespond(request -> {
            now.set(now.get().plusSeconds(10));
            return withServerError().createResponse(request);
        });
        server.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        BotWarmupService service = new BotWarmupService(restTemplate, URL, KEY, Duration.ofMinutes(5), clock);

        assertThat(service.warmup("user-1")).isEqualTo(BotWarmupStatus.DELAYED);
        now.set(now.get().plusSeconds(4));
        assertThat(service.warmup("user-1")).isEqualTo(BotWarmupStatus.DELAYED);
        now.set(now.get().plusSeconds(1));
        assertThat(service.warmup("user-1")).isEqualTo(BotWarmupStatus.READY);
        now.set(now.get().plusSeconds(299));
        assertThat(service.warmup("user-1")).isEqualTo(BotWarmupStatus.READY);
        now.set(now.get().plusSeconds(1));
        assertThat(service.warmup("user-1")).isEqualTo(BotWarmupStatus.READY);
        server.verify();
    }

    @Test
    void doesNotDuplicateInFlightChecksEvenWhenClockPassesCooldown() throws Exception {
        AtomicReference<Instant> now = new AtomicReference<>(CLOCK.instant());
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(ignored -> now.get());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo(URL)).andRespond(request -> {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Warmup was not released");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
            return withSuccess("{}", MediaType.APPLICATION_JSON).createResponse(request);
        });
        BotWarmupService service = new BotWarmupService(restTemplate, URL, KEY, Duration.ofMinutes(5), clock);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> service.warmup("user-1"));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            now.set(now.get().plus(Duration.ofMinutes(6)));
            assertThat(service.warmup("user-1")).isEqualTo(BotWarmupStatus.DELAYED);
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(BotWarmupStatus.READY);
            assertThat(service.warmup("user-1")).isEqualTo(BotWarmupStatus.READY);
            server.verify();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
