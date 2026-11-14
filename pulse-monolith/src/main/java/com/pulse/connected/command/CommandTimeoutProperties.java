package com.pulse.connected.command;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * How long commands may wait.
 *
 * @param queueExpiry      a command queued for an offline vehicle expires this long after it was
 *                         requested (a stale "unlock" must not fire hours later)
 * @param executionTimeout a dispatched command times out this long after it was sent
 */
@ConfigurationProperties("pulse.commands")
public record CommandTimeoutProperties(
        @DefaultValue("PT15M") Duration queueExpiry,
        @DefaultValue("PT60S") Duration executionTimeout) {
}
