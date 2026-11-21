package com.pulse.connected;

import com.pulse.connected.command.CommandService;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.ExtendedLockConfigurationExtractor;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.objenesis.ObjenesisStd;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ShedLock builds each job's lock configuration on every run, and a bad one (lockAtLeastFor longer
 * than lockAtMostFor, or a negative duration) throws on every tick: the scheduler only logs it, so
 * the job silently stops. This builds them with ShedLock's own extractor for a range of intervals.
 */
class ScheduledJobLockDurationTest {

    /** Lock name → the interval property its {@code @Scheduled} reads. */
    private static final Map<String, String> INTERVAL_PROPERTIES = Map.of(
            "outbox-resubmit", "pulse.outbox.resubmit-interval-ms",
            "outbox-purge", "pulse.outbox.purge-interval-ms",
            "connectivity-offline-check", "pulse.connectivity.check-interval-ms",
            "command-pending-flush", "pulse.commands.pending-sweep-interval-ms",
            "ota-update-timeout", "pulse.ota.timeout-check-interval-ms",
            "simulator-heartbeats", "pulse.simulator.heartbeat-interval-ms",
            "simulator-telemetry", "pulse.simulator.telemetry-interval-ms");

    // 500ms (integration tests), 10 minutes (above the 5-minute default lockAtMostFor),
    // 10 days (overflows int arithmetic: 864000000 * 8 > 2^31).
    @ParameterizedTest
    @ValueSource(longs = {500, 600_000, 864_000_000})
    void everyJobGetsAValidLockForItsConfiguredInterval(long intervalMs) {
        Map<String, Object> properties = new java.util.HashMap<>();
        INTERVAL_PROPERTIES.values().forEach(property -> properties.put(property, String.valueOf(intervalMs)));

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("intervals", properties));
            context.register(LockConfig.class);
            context.refresh();
            ExtendedLockConfigurationExtractor extractor = context.getBean(ExtendedLockConfigurationExtractor.class);

            for (Method job : lockedJobs()) {
                String name = job.getAnnotation(SchedulerLock.class).name();
                Object target = new ObjenesisStd().newInstance(job.getDeclaringClass());
                Optional<LockConfiguration> lock = extractor.getLockConfiguration(target, job, new Object[0]);

                assertThat(lock).as(name).isPresent();
                assertThat(lock.get().getLockAtMostFor()).as(name).isGreaterThanOrEqualTo(lock.get().getLockAtLeastFor());
                if (INTERVAL_PROPERTIES.containsKey(name)) {
                    assertThat(lock.get().getLockAtLeastFor()).as(name).isEqualTo(Duration.ofMillis(intervalMs * 8 / 10));
                }
            }
        }
    }

    private static List<Method> lockedJobs() {
        return List.of(
                        "com.pulse.connected.messaging.OutboxMaintenance",
                        CommandService.class.getName(),
                        "com.pulse.connected.vehicle.VehicleConnectivityMonitor",
                        "com.pulse.connected.command.PendingCommandSweep",
                        "com.pulse.connected.ota.OtaUpdateTimeoutSweep",
                        "com.pulse.connected.simulator.VehicleSimulatorRunner").stream()
                .map(name -> ClassUtils.resolveClassName(name, ScheduledJobLockDurationTest.class.getClassLoader()))
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(Scheduled.class))
                .toList();
    }

    @Configuration
    @EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
    static class LockConfig {
        @Bean
        LockProvider lockProvider() {
            return lockConfiguration -> Optional.empty();
        }
    }
}
