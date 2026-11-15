package com.pulse.connected.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

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
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(relay, new ChannelTopic(RedisLiveUpdateRelay.CHANNEL));
        return container;
    }
}
