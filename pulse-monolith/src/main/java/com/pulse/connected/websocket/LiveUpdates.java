package com.pulse.connected.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * transaction (including during another transaction's completion callbacks, where Spring has
 * already cleared the synchronizations) they are sent straight away.
 */
@Component
public class LiveUpdates {

    private static final Logger log = LoggerFactory.getLogger(LiveUpdates.class);

    private final SimpMessageSendingOperations local;
    private final RedisLiveUpdateRelay relay;

    LiveUpdates(SimpMessageSendingOperations local, RedisLiveUpdateRelay relay) {
        this.local = local;
        this.relay = relay;
    }

    public void publish(String destination, Object payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            senderForThisTransaction().pending.add(new Pending(destination, payload));
        } else {
            send(destination, payload);
        }
    }

    private void send(String destination, Object payload) {
        try {
            local.convertAndSend(destination, payload);
            relay.forward(destination, payload);
        } catch (RuntimeException e) {
            log.warn("Could not send live update to {}: {}", destination, e.toString());
        }
    }

    /**
     * The queue lives inside this transaction's synchronization rather than in a bound resource:
     * Spring suspends synchronizations for REQUIRES_NEW / NOT_SUPPORTED, so an inner transaction
     * gets its own queue, and nothing is left on the thread when the transaction ends.
     */
    private SendAfterCommit senderForThisTransaction() {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            if (synchronization instanceof SendAfterCommit sender && sender.owner() == this) {
                return sender;
            }
        }
        SendAfterCommit sender = new SendAfterCommit();
        TransactionSynchronizationManager.registerSynchronization(sender);
        return sender;
    }

    private final class SendAfterCommit implements TransactionSynchronization {

        private final List<Pending> pending = new ArrayList<>();

        LiveUpdates owner() {
            return LiveUpdates.this;
        }

        // afterCompletion, not afterCommit: it also runs for a synchronization registered while
        // other synchronizations' afterCommit callbacks are running.
        @Override
        public void afterCompletion(int status) {
            if (status == STATUS_COMMITTED) {
                pending.forEach(update -> send(update.destination(), update.payload()));
            }
        }
    }

    private record Pending(String destination, Object payload) {
    }
}
