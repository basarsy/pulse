package com.pulse.connected.messaging.dto;

import java.util.UUID;

public record OtaInstructionEvent(
        UUID updateStatusId,
        UUID vehicleId,
        UUID campaignId,
        UUID targetVersionId,
        String targetVersionLabel,
        String packageUrl,
        String checksum) {
}
