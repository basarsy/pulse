package com.pulse.connected.ota;

import com.pulse.connected.domain.UpdateStatus;

import java.util.List;

/**
 * Counts of a campaign's vehicle updates, built from the grouped summary query
 * ({@link VehicleUpdateStatusRepository#countByStageAndStatus}). Answers both the per-stage
 * questions of rollout evaluation and the campaign-wide numbers of the response, so a progress
 * event never has to load every update.
 *
 * <p>Failed means {@code FAILED} or {@code ROLLED_BACK}; finished means installed or failed.
 */
final class CampaignProgress {

    private final List<StageStatusCount> rows;

    private CampaignProgress(List<StageStatusCount> rows) {
        this.rows = List.copyOf(rows);
    }

    static CampaignProgress of(List<StageStatusCount> rows) {
        return new CampaignProgress(rows);
    }

    long total() {
        return rows.stream().mapToLong(StageStatusCount::count).sum();
    }

    long installed() {
        return rows.stream().filter(row -> row.status() == UpdateStatus.INSTALLED).mapToLong(StageStatusCount::count).sum();
    }

    long failed() {
        return rows.stream().filter(row -> isFailed(row.status())).mapToLong(StageStatusCount::count).sum();
    }

    long stageTotal(int stageIndex) {
        return inStage(stageIndex).mapToLong(StageStatusCount::count).sum();
    }

    long stageFailed(int stageIndex) {
        return inStage(stageIndex).filter(row -> isFailed(row.status())).mapToLong(StageStatusCount::count).sum();
    }

    long stageFinished(int stageIndex) {
        return inStage(stageIndex)
                .filter(row -> row.status() == UpdateStatus.INSTALLED || isFailed(row.status()))
                .mapToLong(StageStatusCount::count)
                .sum();
    }

    private java.util.stream.Stream<StageStatusCount> inStage(int stageIndex) {
        return rows.stream().filter(row -> row.stageIndex() == stageIndex);
    }

    private static boolean isFailed(UpdateStatus status) {
        return status == UpdateStatus.FAILED || status == UpdateStatus.ROLLED_BACK;
    }
}
