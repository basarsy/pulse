package com.pulse.connected.ota;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "software_versions")
public class SoftwareVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "version_label", nullable = false, unique = true, length = 50)
    private String versionLabel;

    @Column(name = "release_notes", columnDefinition = "TEXT")
    private String releaseNotes;

    @Column(nullable = false, length = 64)
    private String checksum;

    @Column(name = "package_url", nullable = false, length = 500)
    private String packageUrl;

    @Column(nullable = false)
    private boolean mandatory = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public SoftwareVersion() {}

    public SoftwareVersion(UUID id, String versionLabel, String releaseNotes, String checksum, String packageUrl, boolean mandatory, Instant createdAt) {
        this.id = id;
        this.versionLabel = versionLabel;
        this.releaseNotes = releaseNotes;
        this.checksum = checksum;
        this.packageUrl = packageUrl;
        this.mandatory = mandatory;
        this.createdAt = createdAt;
    }

    public static SoftwareVersionBuilder builder() {
        return new SoftwareVersionBuilder();
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

    public static class SoftwareVersionBuilder {
        private UUID id;
        private String versionLabel;
        private String releaseNotes;
        private String checksum;
        private String packageUrl;
        private boolean mandatory = false;
        private Instant createdAt;

        SoftwareVersionBuilder() {}

        public SoftwareVersionBuilder id(UUID id) { this.id = id; return this; }
        public SoftwareVersionBuilder versionLabel(String versionLabel) { this.versionLabel = versionLabel; return this; }
        public SoftwareVersionBuilder releaseNotes(String releaseNotes) { this.releaseNotes = releaseNotes; return this; }
        public SoftwareVersionBuilder checksum(String checksum) { this.checksum = checksum; return this; }
        public SoftwareVersionBuilder packageUrl(String packageUrl) { this.packageUrl = packageUrl; return this; }
        public SoftwareVersionBuilder mandatory(boolean mandatory) { this.mandatory = mandatory; return this; }
        public SoftwareVersionBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public SoftwareVersion build() {
            return new SoftwareVersion(id, versionLabel, releaseNotes, checksum, packageUrl, mandatory, createdAt);
        }
    }
}
