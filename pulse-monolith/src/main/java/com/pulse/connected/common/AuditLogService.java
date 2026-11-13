package com.pulse.connected.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Async
    public void log(UUID actorUserId, String action, String targetType, String targetId, Map<String, Object> metadata) {
        try {
            AuditLogEntry entry = AuditLogEntry.builder()
                    .actorUserId(actorUserId)
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .metadata(metadata)
                    .build();

            auditLogRepository.save(entry);
            log.debug("Audit log entry saved for action [{}]", action);
        } catch (Exception e) {
            log.error("Failed to save audit log entry for action [{}]: {}", action, e.getMessage(), e);
        }
    }
}
