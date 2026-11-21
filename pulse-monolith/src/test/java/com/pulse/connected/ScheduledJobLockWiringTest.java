package com.pulse.connected;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The real jobs go through their locks: each leaves a row in {@code shedlock}, and the hold time
 * follows the configured interval. All jobs run as soon as the context starts (no initial delay).
 */
class ScheduledJobLockWiringTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void everyJobRunsThroughItsLock() {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(jdbc.queryForList("select name from shedlock", String.class))
                        .contains("outbox-resubmit", "outbox-purge", "command-stale-sweep",
                                "connectivity-offline-check", "simulator-heartbeats", "simulator-telemetry",
                                "command-pending-flush", "ota-update-timeout"));
    }

    @Test
    void lockAtLeastForIs80PercentOfTheConfiguredInterval() {
        // AbstractIntegrationTest sets the heartbeat interval to 500ms, so a released lock lasts 400ms.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("""
                        select extract(epoch from (lock_until - locked_at)) * 1000
                        from shedlock where name = 'simulator-heartbeats'""", Double.class))
                        .isBetween(400.0, 450.0));
    }
}
