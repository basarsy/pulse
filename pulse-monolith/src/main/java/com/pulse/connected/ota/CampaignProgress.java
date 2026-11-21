package com.pulse.connected.ota;

import com.pulse.connected.domain.UpdateStatus;

import java.util.List;
import java.util.stream.Stream;

/**
 * Counts of a campaign's vehicle updates, built from the grouped summary query
 * ({@link VehicleUpdateStatusRepository#countByStageAndStatus}). Answers both the per-stage
 * questions of rollout evaluation and the campaign-wide numbers of the response, so a progress
 * event never has to load every update.
 *
 * <p>Failed means {@code FAILED} or {@code ROLLED_BACK}. Finished means installed, failed or timed
 * out. A timed-out update finishes its stage but is not a failure: it is left out of the failure
 * rate altogether, so silent vehicles neither trip the threshold nor dilute real failures.
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
        return count(rows.stream(), Kind.ANY);
    }

    long installed() {
        return count(rows.stream(), Kind.INSTALLED);
    }

    long failed() {
        return count(rows.stream(), Kind.FAILED);
    }

    long timedOut() {
        return count(rows.stream(), Kind.TIMED_OUT);
    }

    long stageTotal(int stageIndex) {
        return count(inStage(stageIndex), Kind.ANY);
    }

    long stageFailed(int stageIndex) {
        return count(inStage(stageIndex), Kind.FAILED);
    }

    long stageTimedOut(int stageIndex) {
        return count(inStage(stageIndex), Kind.TIMED_OUT);
    }

    long stageFinished(int stageIndex) {
        return count(inStage(stageIndex), Kind.INSTALLED) + stageFailed(stageIndex) + stageTimedOut(stageIndex);
    }

    /**
     * Failed updates as a percentage of the stage's updates that haven't timed out: the vehicles
     * whose outcome is known or still coming. 0 when there are none.
     */
    double stageFailureRate(int stageIndex) {
        long counted = stageTotal(stageIndex) - stageTimedOut(stageIndex);
        return counted == 0 ? 0 : stageFailed(stageIndex) * 100.0 / counted;
    }

    private Stream<StageStatusCount> inStage(int stageIndex) {
        return rows.stream().filter(row -> row.stageIndex() == stageIndex);
    }

    private enum Kind { ANY, INSTALLED, FAILED, TIMED_OUT }

    private static long count(Stream<StageStatusCount> rows, Kind kind) {
        return rows.filter(row -> switch (kind) {
                    case ANY -> true;
                    case INSTALLED -> row.status() == UpdateStatus.INSTALLED;
                    case FAILED -> row.status() == UpdateStatus.FAILED || row.status() == UpdateStatus.ROLLED_BACK;
                    case TIMED_OUT -> row.status() == UpdateStatus.TIMED_OUT;
                })
                .mapToLong(StageStatusCount::count)
                .sum();
    }
}
