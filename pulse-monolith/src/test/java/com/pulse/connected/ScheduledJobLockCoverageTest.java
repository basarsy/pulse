package com.pulse.connected;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every replica runs every {@code @Scheduled} job, so a job without a {@code @SchedulerLock} runs
 * once per replica. This fails as soon as someone adds such a job.
 */
class ScheduledJobLockCoverageTest {

    @Test
    void everyScheduledJobHasAUniquelyNamedLock() {
        List<Method> jobs = scheduledMethods();

        assertThat(jobs).allSatisfy(m -> assertThat(m.getAnnotation(SchedulerLock.class))
                .as("%s.%s has no @SchedulerLock", m.getDeclaringClass().getSimpleName(), m.getName())
                .isNotNull());
        assertThat(jobs).extracting(m -> m.getAnnotation(SchedulerLock.class).name())
                .containsExactlyInAnyOrder("outbox-resubmit", "outbox-purge", "command-stale-sweep",
                        "connectivity-offline-check", "simulator-heartbeats", "simulator-telemetry",
                        "command-pending-flush", "ota-update-timeout");
    }

    private static List<Method> scheduledMethods() {
        AnnotationTypeFilter components = new AnnotationTypeFilter(Component.class);
        // Match on the annotation alone: the default check also evaluates @Conditional, which would
        // hide components that are switched off here, such as the simulator.
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(MetadataReader reader) throws IOException {
                return components.match(reader, getMetadataReaderFactory());
            }
        };
        return scanner.findCandidateComponents(PulseApplication.class.getPackageName()).stream()
                .map(BeanDefinition::getBeanClassName)
                .map(name -> ClassUtils.resolveClassName(name, ScheduledJobLockCoverageTest.class.getClassLoader()))
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(Scheduled.class))
                .toList();
    }
}
