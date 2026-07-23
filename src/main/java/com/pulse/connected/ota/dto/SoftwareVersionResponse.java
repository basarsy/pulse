package com.pulse.connected.ota.dto;

import java.time.Instant;
import java.util.UUID;

public class SoftwareVersionResponse {

    private UUID id;
    private String versionLabel;
    private String releaseNotes;
    private String checksum;
    private String packageUrl;
    private boolean mandatory;
    private Instant createdAt;

    public SoftwareVersionResponse() {}

    public SoftwareVersionResponse(UUID id, String versionLabel, String releaseNotes, String checksum, String packageUrl, boolean mandatory, Instant createdAt) {
        this.id = id;
        this.versionLabel = versionLabel;
        this.releaseNotes = releaseNotes;
        this.checksum = checksum;
        this.packageUrl = packageUrl;
        this.mandatory = mandatory;
        this.createdAt = createdAt;
    }

    public static SoftwareVersionResponseBuilder builder() {
        return new SoftwareVersionResponseBuilder();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getVersionLabel() { return versionLabel; }
    public void setVersionLabel(String versionLabel) { this.versionLabel = versionLabel; }

    public String getReleaseNotes() { return releaseNotes; }
    public void setReleaseNotes(String releaseNotes) { this.releaseNotes = releaseNotes; }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    public String getPackageUrl() { return packageUrl; }
    public void setPackageUrl(String packageUrl) { this.packageUrl = packageUrl; }

    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static class SoftwareVersionResponseBuilder {
        private UUID id;
        private String versionLabel;
        private String releaseNotes;
        private String checksum;
        private String packageUrl;
        private boolean mandatory;
        private Instant createdAt;

        SoftwareVersionResponseBuilder() {}

        public SoftwareVersionResponseBuilder id(UUID id) { this.id = id; return this; }
        public SoftwareVersionResponseBuilder versionLabel(String versionLabel) { this.versionLabel = versionLabel; return this; }
        public SoftwareVersionResponseBuilder releaseNotes(String releaseNotes) { this.releaseNotes = releaseNotes; return this; }
        public SoftwareVersionResponseBuilder checksum(String checksum) { this.checksum = checksum; return this; }
        public SoftwareVersionResponseBuilder packageUrl(String packageUrl) { this.packageUrl = packageUrl; return this; }
        public SoftwareVersionResponseBuilder mandatory(boolean mandatory) { this.mandatory = mandatory; return this; }
        public SoftwareVersionResponseBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public SoftwareVersionResponse build() {
            return new SoftwareVersionResponse(id, versionLabel, releaseNotes, checksum, packageUrl, mandatory, createdAt);
        }
    }
}
