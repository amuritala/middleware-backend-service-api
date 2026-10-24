package com.banking.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** What happened in a governance action, who did it, and why - the immutable audit trail. */
@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The kind of action, e.g. VENDOR_REGISTRATION or ADMIN_ROLE_ASSIGN. */
    @Column(name = "action_type", nullable = false)
    private String actionType;

    /** INITIATED, APPROVED or REJECTED. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditOutcome outcome;

    /** One-line description shown in the audit list. */
    @Column(nullable = false)
    private String summary;

    /** JSON detail of what the action concerned. */
    @Column(columnDefinition = "TEXT")
    private String detail;

    /** Name of whatever the action concerned, e.g. the company or email. */
    @Column(name = "target_label")
    private String targetLabel;

    @Column(name = "actor_keycloak_user_id")
    private String actorKeycloakUserId;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "actor_email")
    private String actorEmail;

    /** The actor's governance role at the time - CONTROL, INITIATOR, AUTHORIZER or SUPER_ADMIN. */
    @Column(name = "actor_role")
    private String actorRole;

    /** The comment the actor supplied, when the action type required one. */
    @Column(columnDefinition = "TEXT")
    private String comment;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
