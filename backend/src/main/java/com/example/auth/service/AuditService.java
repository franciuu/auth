package com.example.auth.service;

import com.example.auth.entity.AuditEventType;
import com.example.auth.entity.AuditLog;
import com.example.auth.entity.Severity;
import com.example.auth.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists security audit events. Each event is written in its own transaction
 * (REQUIRES_NEW) so an audit failure never rolls back the business operation,
 * and audit records survive even if the surrounding transaction rolls back.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEventType eventType, Long userId, Severity severity, String description) {
        try {
            AuditLog entry = AuditLog.builder()
                    .eventType(eventType)
                    .userId(userId)
                    .severity(severity)
                    .description(description)
                    .build();
            auditLogRepository.save(entry);
            log.info("AUDIT [{}] severity={} userId={} - {}", eventType, severity, userId, description);
        } catch (Exception ex) {
            // Never let auditing break the main flow.
            log.error("Failed to persist audit event {}: {}", eventType, ex.getMessage());
        }
    }
}
