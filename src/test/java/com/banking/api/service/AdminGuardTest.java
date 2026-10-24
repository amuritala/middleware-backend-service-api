package com.banking.api.service;

import com.banking.api.entity.AdminRole;
import com.banking.api.entity.Role;
import com.banking.api.entity.User;
import com.banking.api.exception.ApiException;
import com.banking.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The role predicates the controllers put in {@code @PreAuthorize}: only authorizers and
 * super admins may sign off, only control and super admins may manage staff, and only
 * initiators and super admins may propose vendor actions.
 */
class AdminGuardTest {

    private UserRepository userRepository;
    private AdminGuard adminGuard;
    private Authentication authentication;

    private static final String KC_ID = "kc-1";

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        adminGuard = new AdminGuard(userRepository);
        authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt(KC_ID));
    }

    @Test
    void initiatorMayProposeButNeverAuthorizeOrManageStaff() {
        givenUser(AdminRole.INITIATOR);

        assertTrue(adminGuard.canInitiateVendorActions(authentication));
        assertFalse(adminGuard.canAuthorize(authentication));
        assertFalse(adminGuard.canManageStaff(authentication));
        assertFalse(adminGuard.canViewAudit(authentication));
    }

    @Test
    void authorizerMayAuthorizeButNeverPropose() {
        givenUser(AdminRole.AUTHORIZER);

        assertTrue(adminGuard.canAuthorize(authentication));
        assertFalse(adminGuard.canInitiateVendorActions(authentication));
        assertFalse(adminGuard.canManageStaff(authentication));
        assertFalse(adminGuard.canViewAudit(authentication));
    }

    @Test
    void controlManagesStaffButNeitherProposesVendorActionsNorAuthorizes() {
        givenUser(AdminRole.CONTROL);

        assertTrue(adminGuard.canManageStaff(authentication));
        assertFalse(adminGuard.canAuthorize(authentication));
        assertFalse(adminGuard.canInitiateVendorActions(authentication));
        assertFalse(adminGuard.canViewAudit(authentication));
    }

    @Test
    void superAdminHoldsEveryPermission() {
        givenUser(AdminRole.SUPER_ADMIN);

        assertTrue(adminGuard.canManageStaff(authentication));
        assertTrue(adminGuard.canAuthorize(authentication));
        assertTrue(adminGuard.canInitiateVendorActions(authentication));
        assertTrue(adminGuard.canViewAudit(authentication));
    }

    @Test
    void accountWithoutAGovernanceRoleIsRefusedEverywhere() {
        givenUser(null);

        assertFalse(adminGuard.canAuthorize(authentication));
        assertFalse(adminGuard.canManageStaff(authentication));
        assertFalse(adminGuard.canInitiateVendorActions(authentication));
        assertThrows(ApiException.class, () -> adminGuard.requireGovernanceAdmin(authentication));
    }

    @Test
    void missingLocalAccountIsRefusedWithAGovernanceError() {
        when(userRepository.findByKeycloakUserId(KC_ID)).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class,
                () -> adminGuard.requireGovernanceAdmin(authentication));

        assertEquals("GOVERNANCE_ROLE_REQUIRED", error.getErrorCode());
        assertEquals(403, error.getStatus().value());
    }

    // === Helpers ===

    private void givenUser(AdminRole adminRole) {
        when(userRepository.findByKeycloakUserId(KC_ID)).thenReturn(Optional.of(
                User.builder()
                        .id(UUID.randomUUID())
                        .keycloakUserId(KC_ID)
                        .username("one@skybank.sl")
                        .email("one@skybank.sl")
                        .firstName("Test")
                        .lastName("Admin")
                        .role(Role.ROLE_ADMIN)
                        .adminRole(adminRole)
                        .enabled(true)
                        .build()));
    }

    private Jwt jwt(String subject) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("alg", "none"), Map.of("sub", subject));
    }
}
