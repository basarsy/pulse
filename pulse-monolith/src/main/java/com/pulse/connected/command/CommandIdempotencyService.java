package com.pulse.connected.command;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class CommandIdempotencyService {

    private static final String IDEMPOTENCY_PREFIX = "command:idempotency:";
    private static final String RATELIMIT_PREFIX = "ratelimit:commands:";
    private static final int MAX_COMMANDS_PER_MINUTE = 10;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofSeconds(60);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public CommandIdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Idempotency keys are client-chosen, so they are namespaced per user: two users sending
     * the same key must never see each other's commands.
     */
    public Optional<String> getExistingCommandId(UUID userId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(redisTemplate.opsForValue().get(idempotencyRedisKey(userId, idempotencyKey)));
    }

    public void storeIdempotencyKey(UUID userId, String idempotencyKey, UUID commandId) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            redisTemplate.opsForValue().set(
                    idempotencyRedisKey(userId, idempotencyKey),
                    commandId.toString(),
                    IDEMPOTENCY_TTL
            );
        }
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

    private static String idempotencyRedisKey(UUID userId, String idempotencyKey) {
        return IDEMPOTENCY_PREFIX + userId + ":" + idempotencyKey;
    }
}
