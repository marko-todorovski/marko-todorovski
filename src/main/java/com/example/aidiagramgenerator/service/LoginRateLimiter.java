package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.exception.AuthRateLimitedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Throttles repeated failed login attempts, keyed by client IP + normalized email, to slow
 * down credential-stuffing / brute-force attacks against {@code /api/auth/login}.
 *
 * <p>Only failed attempts count against the limit; a successful login clears the counter for
 * that key. The check never reveals whether the account exists - the same generic rate-limit
 * error is returned regardless.</p>
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_KEYS = 5_000;

    private final Map<String, Deque<Instant>> failuresByKey = new LinkedHashMap<>();

    public synchronized void checkAllowed(String remoteAddress, String email) {
        Deque<Instant> failures = failuresByKey.get(key(remoteAddress, email));
        if (failures == null) {
            return;
        }
        prune(failures);
        if (failures.size() >= MAX_FAILED_ATTEMPTS) {
            throw new AuthRateLimitedException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "TOO_MANY_LOGIN_ATTEMPTS",
                    "Too many login attempts. Please try again later.");
        }
    }

    public synchronized void recordFailure(String remoteAddress, String email) {
        Deque<Instant> failures = failuresByKey.computeIfAbsent(key(remoteAddress, email), ignored -> new ArrayDeque<>());
        prune(failures);
        failures.addLast(Instant.now());
        cleanupIfNeeded();
    }

    public synchronized void recordSuccess(String remoteAddress, String email) {
        failuresByKey.remove(key(remoteAddress, email));
    }

    private void prune(Deque<Instant> failures) {
        Instant cutoff = Instant.now().minus(WINDOW);
        while (!failures.isEmpty() && failures.peekFirst().isBefore(cutoff)) {
            failures.removeFirst();
        }
    }

    private void cleanupIfNeeded() {
        if (failuresByKey.size() < MAX_KEYS) {
            return;
        }
        Iterator<Map.Entry<String, Deque<Instant>>> iterator = failuresByKey.entrySet().iterator();
        while (iterator.hasNext()) {
            Deque<Instant> failures = iterator.next().getValue();
            prune(failures);
            if (failures.isEmpty()) {
                iterator.remove();
            }
        }
        while (failuresByKey.size() >= MAX_KEYS && !failuresByKey.isEmpty()) {
            Iterator<String> trim = failuresByKey.keySet().iterator();
            trim.next();
            trim.remove();
        }
    }

    private static String key(String remoteAddress, String email) {
        return safe(remoteAddress) + ':' + safe(email).toLowerCase();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}
