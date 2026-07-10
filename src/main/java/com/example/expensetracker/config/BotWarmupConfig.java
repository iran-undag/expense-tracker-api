package com.example.expensetracker.config;

import com.example.expensetracker.service.BotWarmupService;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class BotWarmupConfig {
    @Bean
    BotWarmupService botWarmupService(
            RestTemplateBuilder builder,
            @Value("${chatbot.warmup.url}") String url,
            @Value("${chatbot.warmup.key}") String key,
            @Value("${chatbot.warmup.cooldown:5m}") Duration cooldown,
            @Value("${chatbot.warmup.connect-timeout:2s}") Duration connectTimeout,
            @Value("${chatbot.warmup.read-timeout:10s}") Duration readTimeout) {
        var restTemplate = builder.setConnectTimeout(connectTimeout).setReadTimeout(readTimeout).build();
        return new BotWarmupService(restTemplate, url, key, cooldown, Clock.systemUTC());
    }
}
