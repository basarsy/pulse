package com.pulse.connected.ota;

import com.pulse.connected.domain.UpdateStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
    }
}
