package com.pulse.connected.ota;

import com.pulse.connected.domain.UpdateStatus;

/** Number of a campaign's vehicle updates in one stage and status (one row of the summary query). */
public record StageStatusCount(int stageIndex, UpdateStatus status, long count) {
}
