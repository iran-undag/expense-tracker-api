package com.example.expensetracker.chattool;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class ChatToolConfig {
    @Bean
    Clock chatToolClock() {
        return Clock.systemUTC();
    }

    @Bean
    ChatToolRateLimiter chatToolRateLimiter(
        Clock clock,
        @Value("${chatbot.tools.requests-per-minute:60}") int requestsPerMinute
    ) {
        return new ChatToolRateLimiter(requestsPerMinute, clock);
    }
}
