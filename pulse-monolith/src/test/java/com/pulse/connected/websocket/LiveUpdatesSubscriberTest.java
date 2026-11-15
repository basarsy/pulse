package com.pulse.connected.websocket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Subscribing to Redis must not be a startup requirement: if Redis is down when the app starts,
 * the app still starts and keeps trying in the background until the subscription is up.
 */
class LiveUpdatesSubscriberTest {

    private final RedisMessageListenerContainer container = mock(RedisMessageListenerContainer.class);
    private final AtomicBoolean listening = new AtomicBoolean();
    private final AtomicInteger attempts = new AtomicInteger();
    private final LiveUpdatesSubscriber subscriber = new LiveUpdatesSubscriber(container, Duration.ofMillis(20));

    @AfterEach
    void stop() {
        subscriber.stop();
    }

    @Test
    void start_ReturnsAtOnce_AndRetriesUntilSubscribed() {
        when(container.isListening()).thenAnswer(invocation -> listening.get());
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RedisConnectionFailureException("down");
            }
            listening.set(true);
            return null;
        }).when(container).start();

        long startedAt = System.nanoTime();
        subscriber.start();
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofMillis(200));

        await().atMost(Duration.ofSeconds(5)).until(listening::get);
        assertThat(subscriber.isRunning()).isTrue();
        int attemptsWhenSubscribed = attempts.get();
        await().during(Duration.ofMillis(200)).atMost(Duration.ofSeconds(1))
                .until(() -> attempts.get() == attemptsWhenSubscribed); // stops trying once subscribed
        assertThat(attemptsWhenSubscribed).isEqualTo(3);
    }

    @Test
    void stop_EndsTheRetriesAndStopsTheContainer() {
        doAnswer(invocation -> {
            attempts.incrementAndGet();
            throw new RedisConnectionFailureException("down");
        }).when(container).start();

        subscriber.start();
        await().atMost(Duration.ofSeconds(5)).until(() -> attempts.get() >= 2);
        subscriber.stop();
        int attemptsWhenStopped = attempts.get();

        await().during(Duration.ofMillis(200)).atMost(Duration.ofSeconds(1))
                .until(() -> attempts.get() <= attemptsWhenStopped + 1);
        assertThat(subscriber.isRunning()).isFalse();
        org.mockito.Mockito.verify(container).stop();
    }
}
