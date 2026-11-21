package com.pulse.connected.ota;

import com.pulse.connected.domain.UpdateStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CampaignProgressTest {

    private final CampaignProgress progress = CampaignProgress.of(List.of(
            new StageStatusCount(0, UpdateStatus.INSTALLED, 3),
            new StageStatusCount(0, UpdateStatus.FAILED, 1),
            new StageStatusCount(1, UpdateStatus.ROLLED_BACK, 2),
            new StageStatusCount(1, UpdateStatus.DOWNLOADING, 4)));

    @Test
    void campaignWideCounts() {
        assertThat(progress.total()).isEqualTo(10);
        assertThat(progress.installed()).isEqualTo(3);
        // ROLLED_BACK counts as failed, as the dashboard has always shown it.
        assertThat(progress.failed()).isEqualTo(3);
    }

    @Test
    void perStageCounts() {
        assertThat(progress.stageTotal(0)).isEqualTo(4);
        assertThat(progress.stageFailed(0)).isEqualTo(1);
        assertThat(progress.stageFinished(0)).isEqualTo(4);

        assertThat(progress.stageTotal(1)).isEqualTo(6);
        assertThat(progress.stageFailed(1)).isEqualTo(2);
        assertThat(progress.stageFinished(1)).isEqualTo(2);
    }

    @Test
    void emptyStage_IsZero() {
        assertThat(progress.stageTotal(5)).isZero();
        assertThat(progress.stageFinished(5)).isZero();
        assertThat(progress.stageFailureRate(5)).isZero();
    }

    /**
     * A timed-out update finishes its stage but isn't a failure, and is left out of the failure rate
     * entirely: it neither raises it nor dilutes real failures.
     */
    @Test
    void timedOut_FinishesButIsLeftOutOfTheFailureRate() {
        CampaignProgress withTimeouts = CampaignProgress.of(List.of(
                new StageStatusCount(0, UpdateStatus.INSTALLED, 5),
                new StageStatusCount(0, UpdateStatus.FAILED, 1),
                new StageStatusCount(0, UpdateStatus.TIMED_OUT, 4)));

        assertThat(withTimeouts.stageFinished(0)).isEqualTo(10);
        assertThat(withTimeouts.stageFailed(0)).isEqualTo(1);
        assertThat(withTimeouts.timedOut()).isEqualTo(4);
        assertThat(withTimeouts.failed()).isEqualTo(1);
        assertThat(withTimeouts.stageFailureRate(0)).as("1 of the 6 that didn't time out").isCloseTo(16.67, within(0.01));
    }

    @Test
    void onlyTimedOut_IsNoFailure() {
        CampaignProgress allSilent = CampaignProgress.of(List.of(new StageStatusCount(0, UpdateStatus.TIMED_OUT, 3)));

        assertThat(allSilent.stageFinished(0)).isEqualTo(3);
        assertThat(allSilent.stageFailureRate(0)).isZero();
    }
}
