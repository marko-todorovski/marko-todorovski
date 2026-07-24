package com.example.aidiagramgenerator.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Shared implementation for generating and hashing opaque bearer tokens
 * (diagram share links, project invitation links). Extracted because
 * {@link DiagramShareTokenService} and {@link InvitationTokenService} used
 * byte-for-byte identical logic; kept as a package-private helper (not a
 * Spring bean) so both services remain distinct, independently injectable
 * types with unchanged public APIs.
 */
final class SecureTokenSupport {

    private static final int TOKEN_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    String hashToken(String rawToken, String missingTokenMessage) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException(missingTokenMessage);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
