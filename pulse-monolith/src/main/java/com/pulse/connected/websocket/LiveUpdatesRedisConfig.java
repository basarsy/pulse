package com.pulse.connected.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.time.Duration;

/** Wires the Redis relay that shares live updates between instances. */
@Configuration
class LiveUpdatesRedisConfig {

    /** Envelopes waiting for Redis; beyond this they are dropped (see {@link RedisLiveUpdateRelay}). */
    private static final int QUEUE_CAPACITY = 1000;

    @Bean
    RedisLiveUpdateRelay redisLiveUpdateRelay(StringRedisTemplate redis, SimpMessageSendingOperations local,
                                              ObjectMapper json, MeterRegistry meters) {
        return new RedisLiveUpdateRelay(redis, local, json, meters, QUEUE_CAPACITY);
    }

    @Bean
    RedisMessageListenerContainer liveUpdatesListenerContainer(RedisConnectionFactory connectionFactory,
                                                               RedisLiveUpdateRelay relay) {
        return listenerContainer(connectionFactory, relay);
    }

    static RedisMessageListenerContainer listenerContainer(RedisConnectionFactory connectionFactory,
                                                           MessageListener listener) {
        // Started by LiveUpdatesSubscriber: auto-starting it would fail the app when Redis is down.
        RedisMessageListenerContainer container = new RedisMessageListenerContainer() {
            @Override
            public boolean isAutoStartup() {
                return false;
            }
        };
        container.setConnectionFactory(connectionFactory);
        // One thread, so updates reach browsers in the order they were sent. The default executor
        // runs each message on a new thread, letting a later update overtake an earlier one.
        SimpleAsyncTaskExecutor dispatcher = new SimpleAsyncTaskExecutor("live-updates-dispatch-");
        dispatcher.setConcurrencyLimit(1);
        container.setTaskExecutor(dispatcher);
        container.addMessageListener(listener, new ChannelTopic(RedisLiveUpdateRelay.CHANNEL));
        return container;
    }

    @Bean
    LiveUpdatesSubscriber liveUpdatesSubscriber(RedisMessageListenerContainer liveUpdatesListenerContainer) {
        return new LiveUpdatesSubscriber(liveUpdatesListenerContainer, Duration.ofSeconds(5));
    }
}
