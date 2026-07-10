package com.example.expensetracker.controller;

import com.example.expensetracker.dto.DirectLineTokenResponseDto;
import com.example.expensetracker.security.CurrentUserService;
import com.example.expensetracker.service.DirectLineTokenService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bot/direct-line")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class BotTokenController {

    private final DirectLineTokenService directLineTokenService;
    private final CurrentUserService currentUserService;

    @PostMapping("/token")
    public ResponseEntity<DirectLineTokenResponseDto> issueToken(Authentication authentication) {
        String userId = currentUserService.getUserId(authentication);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(directLineTokenService.issueToken(userId));
    }
}
