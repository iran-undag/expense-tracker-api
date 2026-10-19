package com.example.expensetracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

public class BotWarmupService {
    private static final Logger log = LoggerFactory.getLogger(BotWarmupService.class);
    private static final String WARMUP_HEADER = "X-Chatbot-Warmup-Key";
    private static final Duration DELAYED_COOLDOWN = Duration.ofSeconds(5);

    private final RestTemplate restTemplate;
    private final String warmupUrl;
    private final String warmupKey;
    private final Duration cooldown;
    private final Clock clock;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public BotWarmupService(RestTemplate restTemplate, String warmupUrl, String warmupKey,
                            Duration cooldown, Clock clock) {
        this.restTemplate = restTemplate;
        this.warmupUrl = warmupUrl;
        this.warmupKey = warmupKey;
        this.cooldown = cooldown;
        this.clock = clock;
    }

    public BotWarmupStatus warmup(String userId) {
        String userHash = hash(userId);
        Instant now = clock.instant();
        CacheEntry existing = cache.get(userHash);
        if (existing != null && existing.isCurrent(now)) {
            return existing.status();
        }
        // An in-flight reservation has no expiry until its downstream request completes.
        CacheEntry reserved = new CacheEntry(BotWarmupStatus.DELAYED, null);
        CacheEntry winner = cache.compute(userHash, (ignored, current) ->
                current != null && current.isCurrent(now) ? current : reserved);
        if (winner != reserved) {
            return winner.status();
        }
        BotWarmupStatus status = callChatbot();
        Duration resultCooldown = status == BotWarmupStatus.READY ? cooldown : DELAYED_COOLDOWN;
        cache.put(userHash, new CacheEntry(status, clock.instant().plus(resultCooldown)));
        return status;
    }

    private BotWarmupStatus callChatbot() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(WARMUP_HEADER, warmupKey);
            restTemplate.exchange(warmupUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return BotWarmupStatus.READY;
        } catch (Exception exception) {
            log.warn("Chatbot warm-up was delayed: type={}", exception.getClass().getSimpleName());
            return BotWarmupStatus.DELAYED;
        }
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record CacheEntry(BotWarmupStatus status, Instant expiresAt) {
        boolean isCurrent(Instant now) {
            return expiresAt == null || now.isBefore(expiresAt);
        }
    }
}
