package com.pulse.connected.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Every replica runs the same {@code @Scheduled} jobs. A {@code @SchedulerLock} on each job makes
 * the replicas take turns through the {@code shedlock} table (V8), so a job runs once per interval
 * across all of them. Lock times come from the database clock, so replica clocks needn't agree.
 * A replica that dies mid-run holds its lock for at most {@code lockAtMostFor}.
 *
 * <p>Each job's lock times are derived from its interval property: {@code lockAtLeastFor} is 80% of
 * the interval, {@code lockAtMostFor} the longer of the interval and 5 minutes (it must never be
 * shorter). Those interval properties ({@code *-interval-ms}) must be plain milliseconds, because
 * the lock expressions do arithmetic on them. {@code ScheduledJobLockDurationTest} covers this.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class SchedulingLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
