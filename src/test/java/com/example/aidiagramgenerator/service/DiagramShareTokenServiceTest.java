package com.example.aidiagramgenerator.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DiagramShareTokenServiceTest {

    private final DiagramShareTokenService tokenService = new DiagramShareTokenService();

    @Test
    void generatedTokensAreUrlSafeHighEntropyAndNotUuidOnly() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String token = tokenService.generateRawToken();
            tokens.add(token);
            assertThat(token).matches("[A-Za-z0-9_-]{43}");
            assertThat(token).doesNotContain("=");
            assertThat(token.length()).isGreaterThan(40);
            assertThat(token).doesNotMatch("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
        }
        assertThat(tokens).hasSize(50);
    }

    @Test
    void hashIsDeterministicHexAndDoesNotExposeRawToken() {
        String token = tokenService.generateRawToken();
        String hash = tokenService.hashToken(token);

        assertThat(hash).isEqualTo(tokenService.hashToken(token));
        assertThat(hash).matches("[0-9a-f]{64}");
        assertThat(hash).isNotEqualTo(token);
        assertThat(tokenService.hashToken(tokenService.generateRawToken())).isNotEqualTo(hash);
    }
}
