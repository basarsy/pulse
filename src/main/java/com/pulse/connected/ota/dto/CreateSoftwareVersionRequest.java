package com.pulse.connected.ota.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateSoftwareVersionRequest {

    @NotBlank(message = "Version label is required")
    private String versionLabel;

    private String releaseNotes;

    @NotBlank(message = "Checksum is required")
    private String checksum;

    @NotBlank(message = "Package URL is required")
    private String packageUrl;

    private boolean mandatory = false;

    public CreateSoftwareVersionRequest() {}

    public CreateSoftwareVersionRequest(String versionLabel, String releaseNotes, String checksum, String packageUrl, boolean mandatory) {
        this.versionLabel = versionLabel;
        this.releaseNotes = releaseNotes;
        this.checksum = checksum;
        this.packageUrl = packageUrl;
        this.mandatory = mandatory;
    }

    public static CreateSoftwareVersionRequestBuilder builder() {
        return new CreateSoftwareVersionRequestBuilder();
    }

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

    public static class CreateSoftwareVersionRequestBuilder {
        private String versionLabel;
        private String releaseNotes;
        private String checksum;
        private String packageUrl;
        private boolean mandatory = false;

        CreateSoftwareVersionRequestBuilder() {}

        public CreateSoftwareVersionRequestBuilder versionLabel(String versionLabel) { this.versionLabel = versionLabel; return this; }
        public CreateSoftwareVersionRequestBuilder releaseNotes(String releaseNotes) { this.releaseNotes = releaseNotes; return this; }
        public CreateSoftwareVersionRequestBuilder checksum(String checksum) { this.checksum = checksum; return this; }
        public CreateSoftwareVersionRequestBuilder packageUrl(String packageUrl) { this.packageUrl = packageUrl; return this; }
        public CreateSoftwareVersionRequestBuilder mandatory(boolean mandatory) { this.mandatory = mandatory; return this; }

        public CreateSoftwareVersionRequest build() {
            return new CreateSoftwareVersionRequest(versionLabel, releaseNotes, checksum, packageUrl, mandatory);
        }
    }
}
