package com.example.aidiagramgenerator.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PublicShareRateLimiter {

    private static final int MAX_METADATA_OR_PREVIEW_PER_MINUTE = 60;
    private static final int MAX_DOWNLOADS_PER_MINUTE = 20;
    private static final int MAX_KEYS = 4_000;

    private final Clock clock;
    private final Map<String, Window> windows = new LinkedHashMap<>();

    public PublicShareRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean allowView(String remoteAddress, String tokenHash) {
        return allow("view", remoteAddress, tokenHash, MAX_METADATA_OR_PREVIEW_PER_MINUTE);
    }

    public synchronized boolean allowDownload(String remoteAddress, String tokenHash) {
        return allow("download", remoteAddress, tokenHash, MAX_DOWNLOADS_PER_MINUTE);
    }

    private boolean allow(String scope, String remoteAddress, String tokenHash, int limit) {
        cleanupIfNeeded();
        long currentMinute = Instant.now(clock).getEpochSecond() / 60;
        String key = scope + ':' + safe(remoteAddress) + ':' + tokenHash;
        Window window = windows.get(key);
        if (window == null || window.minute != currentMinute) {
            windows.put(key, new Window(currentMinute, 1));
            return true;
        }
        if (window.count >= limit) {
            return false;
        }
        window.count++;
        return true;
    }

    private void cleanupIfNeeded() {
        if (windows.size() < MAX_KEYS) {
            return;
        }
        long currentMinute = Instant.now(clock).getEpochSecond() / 60;
        Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().minute < currentMinute) {
                iterator.remove();
            }
        }
        while (windows.size() >= MAX_KEYS && !windows.isEmpty()) {
            Iterator<String> trim = windows.keySet().iterator();
            trim.next();
            trim.remove();
        }
    }

    private static String safe(String remoteAddress) {
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }

    private static final class Window {
        private final long minute;
        private int count;

        private Window(long minute, int count) {
            this.minute = minute;
            this.count = count;
        }
    }
}
