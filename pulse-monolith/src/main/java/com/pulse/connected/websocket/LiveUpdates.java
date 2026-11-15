package com.pulse.connected.websocket;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

/**
 * The one way modules push live updates to browsers. The STOMP broker is in-memory per instance,
 * so an update is delivered to this instance's browsers straight away and also relayed to the
 * other instances, which deliver it to theirs. Never throws and never waits on Redis.
 */
@Component
public class LiveUpdates {

    private final SimpMessageSendingOperations local;
    private final RedisLiveUpdateRelay relay;

    LiveUpdates(SimpMessageSendingOperations local, RedisLiveUpdateRelay relay) {
        this.local = local;
        this.relay = relay;
    }

    public void publish(String destination, Object payload) {
        local.convertAndSend(destination, payload);
        relay.forward(destination, payload);
    }
}
