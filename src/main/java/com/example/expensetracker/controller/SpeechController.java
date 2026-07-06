package com.example.expensetracker.controller;

import com.example.expensetracker.dto.SpeechTokenResponseDto;
import com.example.expensetracker.security.CurrentUserService;
import com.example.expensetracker.service.SpeechTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/speech")
@RequiredArgsConstructor
public class SpeechController {

    private final SpeechTokenService speechTokenService;
    private final CurrentUserService currentUserService;

    @PostMapping("/token")
    public ResponseEntity<SpeechTokenResponseDto> issueToken(Authentication authentication) {
        currentUserService.getUserId(authentication);
        return ResponseEntity.ok(speechTokenService.issueToken());
    }
}
