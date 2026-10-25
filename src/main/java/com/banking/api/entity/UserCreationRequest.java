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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** A queued request to create a bank user, awaiting a decision from an authoriser. */
@Entity
@Table(name = "user_creation_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Null on rows created before clients went through approval; those are bank users. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserCreationKind kind;

    @Column(name = "company_name", length = 255)
    private String companyName;

    /** Full client registration (incl. temporary password) as JSON; cleared once decided. */
    @Column(name = "client_payload", columnDefinition = "TEXT")
    private String clientPayload;

    /** Comma-separated extra realm roles to grant a bank user on approval. */
    @Column(name = "requested_roles", length = 1000)
    private String requestedRoles;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /** Temporary password needed to create the account on approval; cleared once decided. */
    @Column(name = "temporary_password")
    private String temporaryPassword;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserCreationRequestStatus status = UserCreationRequestStatus.PENDING;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public UserCreationKind getKind() {
        return kind == null ? UserCreationKind.BANK_USER : kind;
    }

    public void approve(String reviewer) {
        decide(UserCreationRequestStatus.APPROVED, reviewer);
    }

    public void reject(String reviewer) {
        decide(UserCreationRequestStatus.REJECTED, reviewer);
    }

    private void decide(UserCreationRequestStatus decision, String reviewer) {
        status = decision;
        reviewedBy = reviewer;
        reviewedAt = LocalDateTime.now();
        temporaryPassword = null;
        clientPayload = null;
    }
}
