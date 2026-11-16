package com.pulse.connected.command;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-user fixed-window rate limit for issuing commands, in Redis. (Idempotency is decided by the
 * database, see {@link CommandService#issueCommand}.)
 *
 * <p>Fails open: when Redis is unreachable the limit is skipped, so a Redis outage never stops
 * remote commands. Each skip is counted ({@code pulse.commands.ratelimit.skipped}). A circuit
 * breaker ({@code redisRateLimit}) stops calling Redis after repeated failures, so commands don't
 * each wait out the Redis timeout during an outage.
 */
@Service
public class CommandRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(CommandRateLimiter.class);
    private static final String RATELIMIT_PREFIX = "ratelimit:commands:";
    private static final int MAX_COMMANDS_PER_MINUTE = 10;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofSeconds(60);
    private static final long WARN_INTERVAL_MS = 60_000;

    private final StringRedisTemplate redisTemplate;
    private final CircuitBreaker breaker;
    private final Counter skipped;
    private final AtomicLong lastWarnAt = new AtomicLong(-WARN_INTERVAL_MS);

    public CommandRateLimiter(StringRedisTemplate redisTemplate, CircuitBreakerRegistry breakers, MeterRegistry meters) {
        this.redisTemplate = redisTemplate;
        this.breaker = breakers.circuitBreaker("redisRateLimit");
        this.skipped = meters.counter("pulse.commands.ratelimit.skipped");
    }

    /** True if the user may issue another command now. */
    public boolean checkRateLimit(UUID userId) {
        try {
            return breaker.executeSupplier(() -> withinLimit(userId));
        } catch (DataAccessException | CallNotPermittedException e) {
            skipped.increment();
            warnAtMostOncePerInterval(e);
            return true;
        }
    }

    /**
     * Fixed-window rate limit. The window key is created together with its TTL (SET NX EX)
     * before incrementing, so a crash can never leave a counter without expiry that would
     * lock the user out permanently.
     */
    private boolean withinLimit(UUID userId) {
        String key = RATELIMIT_PREFIX + userId;
        redisTemplate.opsForValue().setIfAbsent(key, "0", RATE_LIMIT_WINDOW);
        Long count = redisTemplate.opsForValue().increment(key);
        return count != null && count <= MAX_COMMANDS_PER_MINUTE;
    }

    private void warnAtMostOncePerInterval(RuntimeException e) {
        long now = System.currentTimeMillis();
        long last = lastWarnAt.get();
        if (now - last >= WARN_INTERVAL_MS && lastWarnAt.compareAndSet(last, now)) {
            log.warn("Redis unavailable, skipping the command rate limit: {}", e.toString());
        }
    }
}
