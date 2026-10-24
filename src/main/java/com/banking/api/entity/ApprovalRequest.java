package com.banking.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.CollectionTable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One maker-checker request: an action an initiator proposed and an authorizer must decide.
 * The payload is a JSON snapshot of what was proposed, so the approval screen can show a
 * faithful record even after the underlying data moves on. The four-eyes rule lives here too
 * - {@code initiatedBy} can never be the one to sign the request off.
 */
@Entity
@Table(name = "approval_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ApprovalActionType actionType;

    /** JSON snapshot of the proposed action - what the approval screen renders. */
    @Column(columnDefinition = "TEXT")
    private String payload;

    /** Human-readable name of whatever this request concerns, e.g. the company or email. */
    @Column(name = "target_label", nullable = false)
    private String targetLabel;

    /** Optional id of the entity the request concerns (client id, user id, ...). */
    @Column(name = "target_id")
    private String targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ApprovalStatus status = ApprovalStatus.PENDING;

    // === Who initiated ===

    @Column(name = "initiated_by", nullable = false)
    private String initiatedBy;

    @Column(name = "initiated_by_name")
    private String initiatedByName;

    @Column(name = "initiated_by_email")
    private String initiatedByEmail;

    @Column(name = "initiated_by_role")
    private String initiatedByRole;

    /** The maker's written justification - mandatory for the stricter action types. */
    @Column(name = "initiated_comment", columnDefinition = "TEXT")
    private String initiatedComment;

    // === Who authorized ===

    @Column(name = "authorized_by")
    private String authorizedBy;

    @Column(name = "authorized_by_name")
    private String authorizedByName;

    @Column(name = "authorized_by_role")
    private String authorizedByRole;

    /** The checker's written rationale - mandatory for the stricter action types. */
    @Column(name = "authorization_comment", columnDefinition = "TEXT")
    private String authorizationComment;

    @Column(name = "authorized_at")
    private LocalDateTime authorizedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** The checker signs off; the action executes immediately after this is persisted. */
    public void approve(String byKeycloakUserId, String byName, String byRole, String comment) {
        this.status = ApprovalStatus.APPROVED;
        this.authorizedBy = byKeycloakUserId;
        this.authorizedByName = byName;
        this.authorizedByRole = byRole;
        this.authorizationComment = comment;
        this.authorizedAt = LocalDateTime.now();
    }

    /** The checker refuses; the proposed action never runs. */
    public void reject(String byKeycloakUserId, String byName, String byRole, String comment) {
        this.status = ApprovalStatus.REJECTED;
        this.authorizedBy = byKeycloakUserId;
        this.authorizedByName = byName;
        this.authorizedByRole = byRole;
        this.authorizationComment = comment;
        this.authorizedAt = LocalDateTime.now();
    }
}
