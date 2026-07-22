package com.example.aidiagramgenerator.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvitationTokenServiceTest {

    private final InvitationTokenService tokenService = new InvitationTokenService();

    @Test
    void generatesHighEntropyUrlSafeTokens() {
        Set<String> tokens = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            String token = tokenService.generateRawToken();
            assertThat(token).matches("[A-Za-z0-9_-]+");
            assertThat(token).hasSizeGreaterThanOrEqualTo(43);
            assertThat(token).doesNotContain("=");
            tokens.add(token);
        }

        assertThat(tokens).hasSize(100);
    }

    @Test
    void hashesWithDeterministicSha256Hex() {
        String token = tokenService.generateRawToken();

        String first = tokenService.hashToken(token);
        String second = tokenService.hashToken(token);

        assertThat(first).isEqualTo(second);
        assertThat(first).matches("[0-9a-f]{64}");
        assertThat(first).isNotEqualTo(token);
    }

    @Test
    void rejectsBlankTokenHashing() {
        assertThatThrownBy(() -> tokenService.hashToken(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invitation token is required");
    }
}
