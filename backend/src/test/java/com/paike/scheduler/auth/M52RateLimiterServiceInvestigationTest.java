package com.paike.scheduler.auth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M52RateLimiterServiceInvestigationTest {

    @Test
    void rateLimiterUsesSingleProcessMemoryState() throws IOException {
        String source = source("src/main/java/com/paike/scheduler/auth/RateLimiterService.java");

        assertTrue(source.contains("ConcurrentHashMap<String, Deque<Long>> attempts"));
        assertTrue(source.contains("attempts.computeIfAbsent(key"));
        assertFalse(source.contains("RedisTemplate"));
        assertFalse(source.contains("StringRedisTemplate"));
    }

    @Test
    void loginRateLimitUsesUsernameAndIpKeys() throws IOException {
        String source = source("src/main/java/com/paike/scheduler/auth/AuthService.java");

        assertTrue(source.contains("\"login:user:\" + request.getUsername()"));
        assertTrue(source.contains("isRateLimited(\"login:user:\" + request.getUsername(), 5, 60_000)"));
        assertTrue(source.contains("\"login:ip:\" + clientIp"));
        assertTrue(source.contains("isRateLimited(\"login:ip:\" + clientIp, 20, 60_000)"));
    }

    @Test
    void expiredEmptyKeysAreCleanedUp() throws IOException {
        String source = source("src/main/java/com/paike/scheduler/auth/RateLimiterService.java");

        assertTrue(source.contains("cleanupExpiredKeysIfDue(now, windowMs)"));
        assertTrue(source.contains("attempts.remove(attemptKey, timestamps)"));
    }

    @Test
    void backendHasNoRedisRateLimiterInfrastructure() throws IOException {
        String pom = source("pom.xml");
        String application = source("src/main/resources/application.yml");
        String combined = pom + "\n" + application;

        assertFalse(combined.contains("spring-boot-starter-data-redis"));
        assertFalse(combined.contains("lettuce"));
        assertFalse(combined.contains("redisson"));
        assertFalse(combined.contains("RedisTemplate"));
    }

    private String source(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        if (Files.exists(direct)) {
            return Files.readString(direct, StandardCharsets.UTF_8);
        }

        Path fromRoot = Path.of("backend").resolve(relativePath);
        if (Files.exists(fromRoot)) {
            return Files.readString(fromRoot, StandardCharsets.UTF_8);
        }

        throw new IOException("Missing source file " + relativePath);
    }
}

