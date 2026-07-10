package com.example.expensetracker.controller;

import com.example.expensetracker.dto.BotWarmupResponseDto;
import com.example.expensetracker.security.CurrentUserService;
import com.example.expensetracker.service.BotWarmupService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bot")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class BotWarmupController {
    private final BotWarmupService warmupService;
    private final CurrentUserService currentUserService;

    @PostMapping("/warmup")
    public ResponseEntity<BotWarmupResponseDto> warmup(Authentication authentication) {
        String userId = currentUserService.getUserId(authentication);
        String status = warmupService.warmup(userId).name().toLowerCase(java.util.Locale.ROOT);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new BotWarmupResponseDto(status));
    }
}
