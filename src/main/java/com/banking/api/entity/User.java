package com.banking.api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "keycloak_user_id", nullable = false, unique = true)
    private String keycloakUserId;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /**
     * Bank staff only: the governance role deciding what this administrator may do inside
     * the admin portal - who may initiate, who may authorize, who assigns roles. Null for
     * vendor users and for staff who have not been classified yet.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "admin_role")
    private AdminRole adminRole;

    @Column(nullable = false)
    private Boolean enabled = true;

    /**
     * True for vendors an administrator created with a temporary password: they must set a new
     * password at first sign-in before the rest of the portal unlocks. Cleared by
     * `PUT /api/account/password`.
     */
    // `default false` matters: ddl-auto=update adds this column to a table that already has
    // rows, and Postgres refuses a NOT NULL column without a default in that case.
    @Column(name = "must_change_password", columnDefinition = "boolean not null default false")
    @Builder.Default
    private Boolean mustChangePassword = false;

    /**
     * Suspended by an approved maker-checker request: the account cannot sign in and every
     * API call from its token is refused until an approved reinstatement clears this flag.
     * Deliberately separate from `enabled`, which tracks the account-provisioning state.
     */
    @Column(name = "suspended", columnDefinition = "boolean not null default false")
    @Builder.Default
    private Boolean suspended = false;

    /**
     * When an approved time-lock expires. Until then the account cannot sign in or call the
     * API; a scheduled job clears the stamp and re-enables the Keycloak user at expiry.
     * Null means no lock is in force.
     */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;
}
