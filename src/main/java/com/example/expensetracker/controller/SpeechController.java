package com.example.expensetracker.controller;

import com.example.expensetracker.dto.SpeechTokenResponseDto;
import com.example.expensetracker.demo.quota.DemoQuotaReservationService;
import java.util.UUID;
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
    private final DemoQuotaReservationService reservationService;

    @PostMapping("/token")
    public ResponseEntity<SpeechTokenResponseDto> issueToken(Authentication authentication) {
        currentUserService.getUserId(authentication);
        UUID reservationId = reservationService.reserve(authentication, 1);
        boolean finalized = false;
        try {
            SpeechTokenResponseDto response = speechTokenService.issueToken();
            reservationService.finalize(reservationId);
            finalized = true;
            return ResponseEntity.ok(response);
        } finally {
            if (!finalized) {
                reservationService.release(reservationId);
            }
        }
    }
}
