package com.pulse.connected.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    void log_persistsAuditEntry() {
        UUID actorId = UUID.randomUUID();
        String action = "TEST_ACTION";
        String targetType = "TestTarget";
        String targetId = "123";
        Map<String, Object> metadata = Map.of("key", "value");

        auditLogService.log(actorId, action, targetType, targetId, metadata);

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogRepository).save(captor.capture());
        
        AuditLogEntry saved = captor.getValue();
        assertEquals(actorId, saved.getActorUserId());
        assertEquals(action, saved.getAction());
        assertEquals(targetType, saved.getTargetType());
        assertEquals(targetId, saved.getTargetId());
        assertEquals(metadata, saved.getMetadata());
    }

    @Test
    void log_withNullMetadata_persistsSuccessfully() {
        UUID actorId = UUID.randomUUID();
        
        auditLogService.log(actorId, "ACTION", "Type", "ID", null);

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogRepository).save(captor.capture());
        
        AuditLogEntry saved = captor.getValue();
        assertNull(saved.getMetadata());
    }

    @Test
    void log_exceptionInRepository_doesNotPropagate() {
        when(auditLogRepository.save(any(AuditLogEntry.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Should not throw exception
        auditLogService.log(UUID.randomUUID(), "ACTION", "Type", "ID", null);

        verify(auditLogRepository).save(any(AuditLogEntry.class));
    }
}
