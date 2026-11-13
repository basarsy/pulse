package com.pulse.connected.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommandIdempotencyServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private CommandIdempotencyService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new CommandIdempotencyService(redisTemplate);
    }

    @Test
    void idempotencyKeys_AreScopedPerUser() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();

        service.storeIdempotencyKey(alice, "same-key", commandId);
        service.getExistingCommandId(bob, "same-key");

        verify(valueOps).set(contains(alice.toString()), eq(commandId.toString()), any(Duration.class));
        verify(valueOps).get(contains(bob.toString()));
        verify(valueOps, never()).get(contains(alice.toString()));
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
}
