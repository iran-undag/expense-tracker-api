package com.example.expensetracker.controller;

import com.example.expensetracker.chattool.ChatToolRequest;
import com.example.expensetracker.chattool.ChatToolResponse;
import com.example.expensetracker.chattool.ChatToolService;
import com.example.expensetracker.chattool.ChatToolRateLimiter;
import com.example.expensetracker.chattool.ChatToolRateLimitException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/chat-tools")
public class InternalChatToolController {
    private final ChatToolService service;
    private final Clock clock;
    private final ChatToolRateLimiter rateLimiter;

    public InternalChatToolController(
        ChatToolService service,
        Clock clock,
        ChatToolRateLimiter rateLimiter
    ) {
        this.service = service;
        this.clock = clock;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/execute")
    public ResponseEntity<ChatToolResponse> execute(@RequestBody ChatToolRequest request) {
        if (!rateLimiter.tryAcquire()) throw new ChatToolRateLimitException();
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(service.execute(request, Instant.now(clock)));
    }
}
