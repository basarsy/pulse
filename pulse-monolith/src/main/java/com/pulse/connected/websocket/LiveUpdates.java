package com.pulse.connected.websocket;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * The one way modules push live updates to browsers. The STOMP broker is in-memory per instance,
 * so an update is delivered to this instance's browsers and also relayed to the other instances,
 * which deliver it to theirs. Never throws and never waits on Redis.
 *
 * <p>Inside a transaction, updates are held until it commits and then sent in publish order; if it
 * rolls back they are dropped, so browsers never see a state that wasn't saved. Outside a
 * transaction they are sent straight away.
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
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            pendingInThisTransaction().add(new Pending(destination, payload));
        } else {
            send(destination, payload);
        }
    }

    private void send(String destination, Object payload) {
        local.convertAndSend(destination, payload);
        relay.forward(destination, payload);
    }

    /** One queue per transaction, so its updates leave together and in order. */
    @SuppressWarnings("unchecked")
    private List<Pending> pendingInThisTransaction() {
        List<Pending> pending = (List<Pending>) TransactionSynchronizationManager.getResource(this);
        if (pending == null) {
            List<Pending> queue = new ArrayList<>();
            TransactionSynchronizationManager.bindResource(this, queue);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                // afterCompletion, not afterCommit: it also runs for a synchronization registered
                // while other synchronizations' afterCommit callbacks are running.
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(LiveUpdates.this);
                    if (status == STATUS_COMMITTED) {
                        queue.forEach(update -> send(update.destination(), update.payload()));
                    }
                }
            });
            pending = queue;
        }
        return pending;
    }

    private record Pending(String destination, Object payload) {
    }
}
