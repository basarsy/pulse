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
        if (softwareVersionRepository.existsByVersionLabel(request.getVersionLabel())) {
            throw new DuplicateResourceException("Software version '" + request.getVersionLabel() + "' already exists");
        }

        SoftwareVersion version = SoftwareVersion.builder()
                .versionLabel(request.getVersionLabel())
                .releaseNotes(request.getReleaseNotes())
                .checksum(request.getChecksum())
                .packageUrl(request.getPackageUrl())
                .mandatory(request.isMandatory())
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
        return SoftwareVersionResponse.builder()
                .id(version.getId())
                .versionLabel(version.getVersionLabel())
                .releaseNotes(version.getReleaseNotes())
                .checksum(version.getChecksum())
                .packageUrl(version.getPackageUrl())
                .mandatory(version.isMandatory())
                .createdAt(version.getCreatedAt())
                .build();
    }
}
