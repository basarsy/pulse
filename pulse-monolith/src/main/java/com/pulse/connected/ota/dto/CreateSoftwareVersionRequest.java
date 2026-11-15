package com.pulse.connected.ota.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSoftwareVersionRequest(
        @NotBlank(message = "Version label is required")
        String versionLabel,

        String releaseNotes,

        @NotBlank(message = "Checksum is required")
        String checksum,

        @NotBlank(message = "Package URL is required")
        String packageUrl,

        boolean mandatory) {
}
