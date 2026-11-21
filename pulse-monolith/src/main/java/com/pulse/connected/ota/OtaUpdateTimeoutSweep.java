package com.pulse.connected.ota;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Times out OTA updates whose vehicle has reported no progress for {@code pulse.ota.update-timeout}
 * (30 minutes by default). A rollout stage only advances once all of its vehicles have finished,
 * so without this one vehicle that went offline mid-download would hold the whole rollout
 * {@code IN_PROGRESS} forever.
 *
 * <p>Each update is timed out in its own transaction under its campaign's lock
 * ({@link RolloutCampaignService#timeOutIfStalled}), like a progress event. A timed-out update finishes its
 * stage but is not a failure, so parked cars never pause a rollout.
 */
@Component
class OtaUpdateTimeoutSweep {

    private static final Logger log = LoggerFactory.getLogger(OtaUpdateTimeoutSweep.class);

    private final VehicleUpdateStatusRepository updateRepository;
    private final RolloutCampaignService campaignService;
    private final Duration timeout;

    OtaUpdateTimeoutSweep(VehicleUpdateStatusRepository updateRepository, RolloutCampaignService campaignService,
                          @Value("${pulse.ota.update-timeout:PT30M}") Duration timeout) {
        this.updateRepository = updateRepository;
        this.campaignService = campaignService;
        this.timeout = timeout;
    }

    @Scheduled(fixedDelayString = "${pulse.ota.timeout-check-interval-ms:60000}")
    @SchedulerLock(name = "ota-update-timeout",
            lockAtLeastFor = "#{${pulse.ota.timeout-check-interval-ms:60000}L * 8 / 10}",
            lockAtMostFor = "#{T(java.lang.Math).max(${pulse.ota.timeout-check-interval-ms:60000}L, 300000L)}")
    void timeOutStalledUpdates() {
        sweep();
    }

    /** One sweep, without the lock (the scheduled entry point above holds it). Returns how many timed out. */
    int sweep() {
        Instant cutoff = Instant.now().minus(timeout);
        String reason = "No progress reported by the vehicle for " + describe(timeout);
        int timedOut = 0;
        for (UUID updateId : updateRepository.findStalledUpdateIds(cutoff)) {
            try {
                if (campaignService.timeOutIfStalled(updateId, cutoff, reason)) {
                    timedOut++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not time out OTA update [{}]: {}", updateId, e.toString());
            }
        }
        if (timedOut > 0) {
            log.info("Timed out {} stalled OTA update(s)", timedOut);
        }
        return timedOut;
    }

    private static String describe(Duration duration) {
        return duration.toMinutes() > 0 ? duration.toMinutes() + " minutes" : duration.toSeconds() + " seconds";
    }
}
