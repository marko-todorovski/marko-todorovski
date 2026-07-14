package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.exception.DiagramAiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DiagramAiAssistantRateLimiter {

    private static final int MAX_REQUESTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<UUID, Deque<Instant>> requestsByUser = new ConcurrentHashMap<>();

    public void check(UUID userId) {
        Instant now = Instant.now();
        Deque<Instant> requests = requestsByUser.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        synchronized (requests) {
            while (!requests.isEmpty() && requests.peekFirst().plus(WINDOW).isBefore(now)) {
                requests.removeFirst();
            }
            if (requests.size() >= MAX_REQUESTS) {
                throw new DiagramAiException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "AI_RATE_LIMITED",
                        "Too many AI requests. Try again shortly.");
            }
            requests.addLast(now);
        }
    }
}
