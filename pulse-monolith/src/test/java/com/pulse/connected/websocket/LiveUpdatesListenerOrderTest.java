package com.pulse.connected.websocket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.testcontainers.containers.GenericContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Updates must reach the receiving pod's browsers in the order they were sent: the dashboard keeps
 * the last one it gets. Spring Data Redis dispatches each message on a new thread by default, so
 * a slow first message would be overtaken by the second.
 */
class LiveUpdatesListenerOrderTest {

    private final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    private LettuceConnectionFactory connectionFactory;
    private RedisMessageListenerContainer container;

    @AfterEach
    void tearDown() throws Exception {
        if (container != null) {
            container.destroy();
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        redis.stop();
    }

    @Test
    void messagesAreHandledInTheOrderTheyWereSent_EvenWhenOneIsSlow() {
        redis.start();
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        List<String> handled = new CopyOnWriteArrayList<>();
        container = LiveUpdatesRedisConfig.listenerContainer(connectionFactory, (message, pattern) -> {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            if (body.equals("first")) {
                sleep(500);
            }
            handled.add(body);
        });
        container.afterPropertiesSet();
        container.start();
        await().atMost(Duration.ofSeconds(10)).until(container::isListening);

        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.convertAndSend(RedisLiveUpdateRelay.CHANNEL, "first");
        template.convertAndSend(RedisLiveUpdateRelay.CHANNEL, "second");

        await().atMost(Duration.ofSeconds(5)).until(() -> handled.size() == 2);
        assertThat(handled).containsExactly("first", "second");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
