package com.pulse.connected.command;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommandRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    // Opens after 2 calls if they all failed, so the test can see it open.
    private final CircuitBreakerRegistry breakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
            .slidingWindowSize(2)
            .minimumNumberOfCalls(2)
            .waitDurationInOpenState(Duration.ofMinutes(1))
            .recordExceptions(DataAccessException.class)
            .build());

    private CommandRateLimiter service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new CommandRateLimiter(redisTemplate, breakers, meters);
    }

    @Test
    void rateLimitWindow_IsCreatedWithTtlBeforeIncrementing() {
        UUID user = UUID.randomUUID();
        when(valueOps.increment(anyString())).thenReturn(1L);

        assertThat(service.checkRateLimit(user)).isTrue();

        InOrder inOrder = inOrder(valueOps);
        inOrder.verify(valueOps).setIfAbsent(contains(user.toString()), eq("0"), eq(Duration.ofSeconds(60)));
        inOrder.verify(valueOps).increment(contains(user.toString()));
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void rateLimit_RejectsAfterTenCommandsInWindow() {
        when(valueOps.increment(anyString())).thenReturn(11L);

        assertThat(service.checkRateLimit(UUID.randomUUID())).isFalse();
    }

    @Test
    void redisDown_SkipsTheLimit_AndCountsIt() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThat(service.checkRateLimit(UUID.randomUUID())).isTrue();
        assertThat(meters.counter("pulse.commands.ratelimit.skipped").count()).isEqualTo(1);
    }

    @Test
    void redisDown_BreakerOpens_AndLaterCallsSkipRedis() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        for (int i = 0; i < 5; i++) {
            assertThat(service.checkRateLimit(UUID.randomUUID())).isTrue();
        }

        verify(valueOps, times(2)).setIfAbsent(anyString(), anyString(), any(Duration.class));
        assertThat(meters.counter("pulse.commands.ratelimit.skipped").count()).isEqualTo(5);
    }

    @Test
    void otherFailures_StillPropagate() {
        when(valueOps.increment(anyString())).thenThrow(new IllegalStateException("bug"));

        assertThatThrownBy(() -> service.checkRateLimit(UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
        assertThat(meters.counter("pulse.commands.ratelimit.skipped").count()).isZero();
    }
}
