package com.example.auth.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_logs_user_id", columnList = "user_id"),
                @Index(name = "idx_audit_logs_event_type", columnList = "event_type")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    private AuditEventType eventType;

    /** Nullable: some events (e.g. failed login for unknown email) have no user. */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "severity", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    @PrePersist
    void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
