package com.example.expensetracker.demo.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class DemoTokenDigester {

    private static final int MINIMUM_KEY_BYTES = 32;
    private static final int ACCESS_TOKEN_BYTES = 32;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public DemoTokenDigester(String rawKey) {
        byte[] keyBytes = rawKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MINIMUM_KEY_BYTES) {
            throw new IllegalArgumentException("Demo token HMAC key must be at least 32 bytes.");
        }
        this.key = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
    }

    public String digest(String rawToken) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Unable to hash demo token.", exception);
        }
    }

    public boolean matches(String rawToken, String expectedDigest) {
        if (expectedDigest == null) {
            return false;
        }
        return MessageDigest.isEqual(
            digest(rawToken).getBytes(StandardCharsets.US_ASCII),
            expectedDigest.getBytes(StandardCharsets.US_ASCII)
        );
    }

    public String generateAccessToken() {
        byte[] randomBytes = new byte[ACCESS_TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        return DemoBearerTokenResolver.TOKEN_PREFIX
            + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
