package com.pulse.connected.vehicle;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param offlineAfter an ONLINE vehicle without a heartbeat for this long is marked OFFLINE
 */
@ConfigurationProperties("pulse.connectivity")
public record ConnectivityProperties(@DefaultValue("PT60S") Duration offlineAfter) {
}
