package com.example.expensetracker.demo.session;

import java.time.OffsetDateTime;

public record DemoSessionResponse(
    String accessToken,
    OffsetDateTime accessTokenExpiresAt,
    OffsetDateTime sessionExpiresAt,
    int actionLimit,
    int usedActions,
    int remainingActions
) {
}
