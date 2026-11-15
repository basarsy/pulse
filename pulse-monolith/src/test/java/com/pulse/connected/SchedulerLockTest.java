package com.pulse.connected;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two instances firing the same scheduled job at the same moment must run it once. The lock lives
 * in Postgres, so two threads in one JVM race for it exactly as two replicas would.
 */
@Import(SchedulerLockTest.Config.class)
class SchedulerLockTest extends AbstractIntegrationTest {

    @Autowired
    private HeldJob job;

    @Autowired
    private JdbcTemplate jdbc;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutDown() {
        job.release();
        executor.shutdownNow();
    }

    @Test
    void secondConcurrentRun_IsSkippedWhileTheFirstHoldsTheLock() throws Exception {
        Future<?> first = executor.submit(job::run);
        assertThat(job.awaitStarted()).isTrue();

        // A separate connection already sees the lock while the job's transaction is still open,
        // so another instance would see it too.
        // With usingDbTime() ShedLock stores UTC wall-clock time, so compare against UTC in SQL.
        Boolean held = jdbc.queryForObject(
                "select lock_until > (now() at time zone 'utc') from shedlock where name = 'test-held-job'",
                Boolean.class);
        assertThat(held).isTrue();

        executor.submit(job::run).get(5, SECONDS); // returns at once: the lock is taken
        job.release();
        first.get(5, SECONDS);

        assertThat(job.runs()).isEqualTo(1);
    }

    @TestConfiguration
    static class Config {
        @Bean
        HeldJob heldJob() {
            return new HeldJob();
        }
    }

    /**
     * A job that, once started, stays mid-run until the test releases it. The bean is a proxy, so
     * the test reaches its state through methods, not fields.
     */
    static class HeldJob {
        private final AtomicInteger runs = new AtomicInteger();
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        public int runs() {
            return runs.get();
        }

        public boolean awaitStarted() throws InterruptedException {
            return started.await(5, SECONDS);
        }

        public void release() {
            release.countDown();
        }

        @Transactional
        @SchedulerLock(name = "test-held-job", lockAtMostFor = "PT1M")
        public void run() {
            runs.incrementAndGet();
            started.countDown();
            try {
                release.await(10, SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
