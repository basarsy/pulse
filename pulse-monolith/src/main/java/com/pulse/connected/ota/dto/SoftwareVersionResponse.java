package com.pulse.connected.ota.dto;

import java.time.Instant;
import java.util.UUID;

public record SoftwareVersionResponse(
        UUID id,
        String versionLabel,
        String releaseNotes,
        String checksum,
        String packageUrl,
        boolean mandatory,
        Instant createdAt) {
}
