package com.pulse.connected.messaging;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Exports the transactional outbox backlog. A value that stays above zero means messages for
 * vehicles are not being delivered to Kafka (e.g. the broker is unreachable).
 */
@Component
class OutboxMetrics {

    OutboxMetrics(MeterRegistry registry, JdbcTemplate jdbc) {
        Gauge.builder("pulse.outbox.incomplete", jdbc, OutboxMetrics::countIncomplete)
                .description("Outbox publications not yet delivered")
                .register(registry);
    }

    private static double countIncomplete(JdbcTemplate jdbc) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_publication WHERE completion_date IS NULL", Integer.class);
        return count == null ? 0 : count;
    }
}
