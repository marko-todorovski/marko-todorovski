package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.exception.ProjectInvitationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class ProjectInvitationRateLimiter {

    private static final int MAX_INVITES_PER_HOUR = 20;
    private static final int MAX_KEYS = 2000;
    private final Map<String, Counter> counters = new LinkedHashMap<>();

    public synchronized void check(UUID projectId, UUID userId) {
        Instant window = Instant.now().truncatedTo(ChronoUnit.HOURS);
        cleanup(window);
        String key = userId + ":" + projectId;
        Counter counter = counters.computeIfAbsent(key, ignored -> new Counter(window, 0));
        if (!counter.window.equals(window)) {
            counter.window = window;
            counter.count = 0;
        }
        counter.count++;
        if (counter.count > MAX_INVITES_PER_HOUR) {
            throw new ProjectInvitationException(HttpStatus.TOO_MANY_REQUESTS, "INVITATION_RATE_LIMITED",
                    "Too many invitations. Try again later.");
        }
    }

    private void cleanup(Instant currentWindow) {
        Iterator<Map.Entry<String, Counter>> iterator = counters.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Counter> entry = iterator.next();
            if (counters.size() > MAX_KEYS || entry.getValue().window.isBefore(currentWindow.minus(1, ChronoUnit.HOURS))) {
                iterator.remove();
            }
        }
    }

    private static final class Counter {
        private Instant window;
        private int count;

        private Counter(Instant window, int count) {
            this.window = window;
            this.count = count;
        }
    }
}
