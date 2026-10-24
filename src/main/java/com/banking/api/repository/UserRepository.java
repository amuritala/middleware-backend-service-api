package com.banking.api.repository;

import com.banking.api.entity.Role;
import com.banking.api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByKeycloakUserId(String keycloakUserId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByKeycloakUserId(String keycloakUserId);

    /** Every bank staff member holding a governance role, newest account first. */
    @Query("SELECT u FROM User u WHERE u.role = :role ORDER BY u.createdAt DESC")
    List<User> findStaffByRole(@Param("role") Role role);

    /** Accounts whose time lock has run out and now need releasing. */
    @Query("SELECT u FROM User u WHERE u.lockedUntil IS NOT NULL AND u.lockedUntil <= :now")
    List<User> findExpiredLocks(@Param("now") LocalDateTime now);

    /**
     * One-shot promotion of legacy administrators to SUPER_ADMIN. Runs at every startup but
     * only ever matches rows that have no governance role yet, so it stays idempotent.
     */
    @Modifying
    @Query("UPDATE User u SET u.adminRole = com.banking.api.entity.AdminRole.SUPER_ADMIN "
            + "WHERE u.role = com.banking.api.entity.Role.ROLE_ADMIN AND u.adminRole IS NULL")
    int promoteAdminsToSuperAdmin();
}