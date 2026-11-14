package com.pulse.connected.command;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Per-user fixed-window rate limit for issuing commands, in Redis. (Idempotency is decided by the
 * database, see {@link CommandService#issueCommand}.)
 */
@Service
public class CommandRateLimiter {

    private static final String RATELIMIT_PREFIX = "ratelimit:commands:";
    private static final int MAX_COMMANDS_PER_MINUTE = 10;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;

    public CommandRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Fixed-window rate limit. The window key is created together with its TTL (SET NX EX)
     * before incrementing, so a crash can never leave a counter without expiry that would
     * lock the user out permanently.
     */
    public boolean checkRateLimit(UUID userId) {
        String key = RATELIMIT_PREFIX + userId;
        redisTemplate.opsForValue().setIfAbsent(key, "0", RATE_LIMIT_WINDOW);
        Long count = redisTemplate.opsForValue().increment(key);
        return count != null && count <= MAX_COMMANDS_PER_MINUTE;
    }
}
