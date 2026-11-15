package com.pulse.connected.ota;

import com.pulse.connected.common.exception.DuplicateResourceException;
import com.pulse.connected.common.exception.ResourceNotFoundException;
import com.pulse.connected.ota.dto.CreateSoftwareVersionRequest;
import com.pulse.connected.ota.dto.SoftwareVersionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SoftwareVersionService {

    private final SoftwareVersionRepository softwareVersionRepository;

    public SoftwareVersionService(SoftwareVersionRepository softwareVersionRepository) {
        this.softwareVersionRepository = softwareVersionRepository;
    }

    @Transactional
    public SoftwareVersionResponse registerSoftwareVersion(CreateSoftwareVersionRequest request) {
        if (softwareVersionRepository.existsByVersionLabel(request.versionLabel())) {
            throw new DuplicateResourceException("Software version '" + request.versionLabel() + "' already exists");
        }

        SoftwareVersion version = SoftwareVersion.builder()
                .versionLabel(request.versionLabel())
                .releaseNotes(request.releaseNotes())
                .checksum(request.checksum())
                .packageUrl(request.packageUrl())
                .mandatory(request.mandatory())
                .build();

        SoftwareVersion saved = softwareVersionRepository.save(version);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public SoftwareVersionResponse getSoftwareVersionById(UUID id) {
        SoftwareVersion version = softwareVersionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Software version not found with ID: " + id));
        return mapToResponse(version);
    }

    @Transactional(readOnly = true)
    public List<SoftwareVersionResponse> getAllSoftwareVersions() {
        return softwareVersionRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public SoftwareVersionResponse mapToResponse(SoftwareVersion version) {
        return new SoftwareVersionResponse(
                version.getId(),
                version.getVersionLabel(),
                version.getReleaseNotes(),
                version.getChecksum(),
                version.getPackageUrl(),
                version.isMandatory(),
                version.getCreatedAt());
    }
}
