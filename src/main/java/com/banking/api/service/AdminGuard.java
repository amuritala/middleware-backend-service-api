package com.banking.api.service;

import com.banking.api.entity.AdminRole;
import com.banking.api.entity.User;
import com.banking.api.exception.ApiException;
import com.banking.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves the signed-in administrator from the JWT and answers the governance questions the
 * maker-checker rules are built from. Exposed as the Spring bean {@code adminGuard} so the
 * controllers can write them straight into {@code @PreAuthorize} expressions - access control
 * is enforced on the server, never merely hidden in the UI.
 */
@Component("adminGuard")
@RequiredArgsConstructor
@Slf4j
public class AdminGuard {

    private final UserRepository userRepository;

    // === Current administrator ===

    /** The local user row behind the calling JWT, if there is one. */
    public Optional<User> currentAdmin(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        return userRepository.findByKeycloakUserId(jwt.getSubject());
    }

    /**
     * The calling administrator, which must hold a governance role. Vendor accounts and
     * staff who predate the rollout (no governance role assigned yet) are refused: only
     * Control or a Super Admin can classify them.
     */
    public User requireGovernanceAdmin(Authentication authentication) {
        return currentAdmin(authentication)
                .filter(user -> user.getAdminRole() != null)
                .orElseThrow(() -> new ApiException(
                        "GOVERNANCE_ROLE_REQUIRED",
                        "Your account does not hold a governance role yet. Ask a Control or Super Admin administrator to assign one.",
                        HttpStatus.FORBIDDEN
                ));
    }

    // === Role predicates, used from @PreAuthorize ===

    public boolean isSuperAdmin(Authentication authentication) {
        return hasRole(authentication, AdminRole.SUPER_ADMIN);
    }

    public boolean isControl(Authentication authentication) {
        return hasRole(authentication, AdminRole.CONTROL);
    }

    public boolean isInitiator(Authentication authentication) {
        return hasRole(authentication, AdminRole.INITIATOR);
    }

    public boolean isAuthorizer(Authentication authentication) {
        return hasRole(authentication, AdminRole.AUTHORIZER);
    }

    /** Vendor registration and vendor lifecycle actions: initiators and super admins. */
    public boolean canInitiateVendorActions(Authentication authentication) {
        return hasAnyRole(authentication, AdminRole.SUPER_ADMIN, AdminRole.INITIATOR);
    }

    /** Admin user creation and role assignment: control and super admins. */
    public boolean canManageStaff(Authentication authentication) {
        return hasAnyRole(authentication, AdminRole.SUPER_ADMIN, AdminRole.CONTROL);
    }

    /** Signing off requests: authorizers and super admins - never the initiator themselves. */
    public boolean canAuthorize(Authentication authentication) {
        return hasAnyRole(authentication, AdminRole.SUPER_ADMIN, AdminRole.AUTHORIZER);
    }

    /** The governance audit trail is a super-admin screen. */
    public boolean canViewAudit(Authentication authentication) {
        return hasRole(authentication, AdminRole.SUPER_ADMIN);
    }

    // === Helpers ===

    private boolean hasRole(Authentication authentication, AdminRole role) {
        return currentAdmin(authentication)
                .map(user -> user.getAdminRole() == role)
                .orElse(false);
    }

    private boolean hasAnyRole(Authentication authentication, AdminRole... roles) {
        Optional<AdminRole> current = currentAdmin(authentication).map(User::getAdminRole);
        if (current.isEmpty()) {
            return false;
        }
        for (AdminRole role : roles) {
            if (current.get() == role) {
                return true;
            }
        }
        return false;
    }
}
