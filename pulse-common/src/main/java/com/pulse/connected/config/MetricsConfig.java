package com.pulse.connected.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> {
            registry.counter("pulse.commands.issued", "type", "UNKNOWN");
            registry.counter("pulse.commands.completed");
            registry.counter("pulse.commands.failed");
            registry.counter("pulse.commands.timed_out");
            registry.counter("pulse.telemetry.ingested");
        };
    }
}
