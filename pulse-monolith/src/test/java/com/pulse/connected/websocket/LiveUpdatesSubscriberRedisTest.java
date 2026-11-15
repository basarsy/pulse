package com.pulse.connected.websocket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.testcontainers.containers.FixedHostPortGenericContainer;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;

import static org.awaitility.Awaitility.await;

/**
 * Against a real Redis that only comes up after the app: Spring Data Redis' container cannot be
 * started again after a failed start() unless it is stopped first, which a mocked container hides.
 */
class LiveUpdatesSubscriberRedisTest {

    private FixedHostPortGenericContainer<?> redis;
    private LettuceConnectionFactory connectionFactory;
    private RedisMessageListenerContainer container;
    private LiveUpdatesSubscriber subscriber;

    @AfterEach
    void tearDown() throws Exception {
        if (subscriber != null) {
            subscriber.stop();
        }
        if (container != null) {
            container.destroy();
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redis != null) {
            redis.stop();
        }
    }

    @Test
    @SuppressWarnings("deprecation") // a fixed port is the point: Redis appears later at a known address
    void subscribesOnceRedisComesUp_AfterStartingWithoutIt() throws Exception {
        int port = freePort();
        connectionFactory = new LettuceConnectionFactory("localhost", port);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> { }, new ChannelTopic(RedisLiveUpdateRelay.CHANNEL));
        container.afterPropertiesSet();

        subscriber = new LiveUpdatesSubscriber(container, Duration.ofMillis(200));
        subscriber.start();
        Thread.sleep(1500); // a few failed attempts while Redis is down

        redis = new FixedHostPortGenericContainer<>("redis:7-alpine").withFixedExposedPort(port, 6379);
        redis.start();

        await().atMost(Duration.ofSeconds(20)).until(container::isListening);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
