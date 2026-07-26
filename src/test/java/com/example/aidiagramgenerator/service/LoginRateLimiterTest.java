package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.exception.AuthRateLimitedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    @Test
    void allowsUpToFiveFailedAttemptsThenBlocksTheSixth() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        String ip = "10.0.0.1";
        String email = "attacker@example.com";

        for (int i = 0; i < 5; i++) {
            assertThatCode(() -> limiter.checkAllowed(ip, email)).doesNotThrowAnyException();
            limiter.recordFailure(ip, email);
        }

        assertThatThrownBy(() -> limiter.checkAllowed(ip, email))
                .isInstanceOf(AuthRateLimitedException.class)
                .satisfies(ex -> {
                    AuthRateLimitedException rateLimited = (AuthRateLimitedException) ex;
                    assertThat(rateLimited.getCode()).isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
                    assertThat(rateLimited.getMessage()).isEqualTo("Too many login attempts. Please try again later.");
                });
    }

    @Test
    void successfulLoginClearsFailureCountForThatKey() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        String ip = "10.0.0.2";
        String email = "user@example.com";

        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(ip, email);
        }
        limiter.recordSuccess(ip, email);

        assertThatCode(() -> limiter.checkAllowed(ip, email)).doesNotThrowAnyException();

        // Failure count should have reset to zero, so four more failures should still be allowed.
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(ip, email);
        }
        assertThatCode(() -> limiter.checkAllowed(ip, email)).doesNotThrowAnyException();
    }

    @Test
    void failuresAreScopedByIpAndEmailIndependently() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("10.0.0.3", "victim@example.com");
        }
        assertThatThrownBy(() -> limiter.checkAllowed("10.0.0.3", "victim@example.com"))
                .isInstanceOf(AuthRateLimitedException.class);

        // Different IP, same email -> not blocked.
        assertThatCode(() -> limiter.checkAllowed("10.0.0.4", "victim@example.com"))
                .doesNotThrowAnyException();

        // Same IP, different email -> not blocked.
        assertThatCode(() -> limiter.checkAllowed("10.0.0.3", "someone-else@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void emailMatchingIsCaseInsensitive() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        String ip = "10.0.0.5";

        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(ip, "Mixed.Case@Example.com");
        }

        assertThatThrownBy(() -> limiter.checkAllowed(ip, "mixed.case@example.com"))
                .isInstanceOf(AuthRateLimitedException.class);
    }
}
