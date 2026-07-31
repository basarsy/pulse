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

    private final StringRedisTemplate redisTemplate;

    public CommandIdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<String> getExistingCommandId(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        String value = redisTemplate.opsForValue().get(IDEMPOTENCY_PREFIX + idempotencyKey);
        return Optional.ofNullable(value);
    }

    public void storeIdempotencyKey(String idempotencyKey, UUID commandId) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            redisTemplate.opsForValue().set(
                    IDEMPOTENCY_PREFIX + idempotencyKey,
                    commandId.toString(),
                    Duration.ofHours(24)
            );
        }
    }

    public boolean checkRateLimit(UUID userId) {
        String key = RATELIMIT_PREFIX + userId.toString();
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(60));
        }
        return count != null && count <= MAX_COMMANDS_PER_MINUTE;
    }
}
