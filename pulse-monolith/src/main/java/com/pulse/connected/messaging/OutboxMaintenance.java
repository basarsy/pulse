package com.pulse.connected.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Keeps the transactional outbox (Spring Modulith's event_publication table) healthy:
 * resubmits publications whose listener failed (e.g. Kafka was unavailable) and purges
 * completed publications after a retention period.
 */
@Component
class OutboxMaintenance {

    private static final Logger log = LoggerFactory.getLogger(OutboxMaintenance.class);

    private final IncompleteEventPublications incomplete;
    private final CompletedEventPublications completed;
    private final Duration retryAfter;
    private final Duration retention;

    OutboxMaintenance(IncompleteEventPublications incomplete,
                      CompletedEventPublications completed,
                      @Value("${pulse.outbox.retry-after:PT1M}") Duration retryAfter,
                      @Value("${pulse.outbox.retention:P7D}") Duration retention) {
        this.incomplete = incomplete;
        this.completed = completed;
        this.retryAfter = retryAfter;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${pulse.outbox.resubmit-interval-ms:60000}")
    void resubmitFailedPublications() {
        incomplete.resubmitIncompletePublicationsOlderThan(retryAfter);
    }

    @Scheduled(fixedDelayString = "${pulse.outbox.purge-interval-ms:3600000}")
    void purgeCompletedPublications() {
        completed.deletePublicationsOlderThan(retention);
        log.debug("Purged completed outbox publications older than {}", retention);
    }
}
