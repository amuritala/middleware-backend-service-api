package com.banking.api.entity;

import jakarta.persistence.CollectionTable;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "role_change_applications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleChangeApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "keycloak_user_id", nullable = false)
    private String keycloakUserId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "role_change_application_roles",
            joinColumns = @JoinColumn(name = "application_id")
    )
    @Column(name = "role_name", nullable = false)
    @Builder.Default
    private Set<String> requestedRoles = new LinkedHashSet<>();

    /**
     * The roles the vendor did not hold when they submitted - the part of the request
     * that is genuinely new and needs an administrator's attention. `requestedRoles`
     * stays the complete target set so approval keeps replacing the whole role set.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "role_change_application_new_roles",
            joinColumns = @JoinColumn(name = "application_id")
    )
    @Column(name = "role_name", nullable = false)
    @Builder.Default
    private Set<String> newlyRequestedRoles = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RoleChangeApplicationStatus status = RoleChangeApplicationStatus.PENDING;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public void approve(String reviewer) {
        status = RoleChangeApplicationStatus.APPROVED;
        reviewedBy = reviewer;
        reviewedAt = LocalDateTime.now();
    }

    public void reject(String reviewer) {
        status = RoleChangeApplicationStatus.REJECTED;
        reviewedBy = reviewer;
        reviewedAt = LocalDateTime.now();
    }
}
