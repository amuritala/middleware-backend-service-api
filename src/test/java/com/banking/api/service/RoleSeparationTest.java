package com.banking.api.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleSeparationTest {

    @Test
    void requesterRolesMayBeCombined() {
        assertDoesNotThrow(() -> AdminService.ensureMakerCheckerSeparation(
                Set.of("ROLE_ADMIN", "ROLE_CREATE_USER", "ROLE_REGISTER_VENDOR")));
        assertDoesNotThrow(() -> AdminService.ensureMakerCheckerSeparation(
                Set.of("ROLE_ADMIN", "ROLE_AUTHORISE_USER_CREATION")));
    }

    @Test
    void authoriserCannotBeCombinedWithRequesterRoles() {
        assertThrows(IllegalArgumentException.class, () -> AdminService.ensureMakerCheckerSeparation(
                Set.of("ROLE_AUTHORISE_USER_CREATION", "ROLE_CREATE_USER")));
        assertThrows(IllegalArgumentException.class, () -> AdminService.ensureMakerCheckerSeparation(
                Set.of("ROLE_AUTHORISE_USER_CREATION", "ROLE_REGISTER_VENDOR")));
    }

    @Test
    void registrationAuthoriserCannotBeCombinedWithRegisterUser() {
        assertThrows(IllegalArgumentException.class, () -> AdminService.ensureMakerCheckerSeparation(
                Set.of("ROLE_AUTHORISE_REGISTRATION", "ROLE_REGISTER_VENDOR")));
        assertDoesNotThrow(() -> AdminService.ensureMakerCheckerSeparation(
                Set.of("ROLE_AUTHORISE_REGISTRATION", "ROLE_CREATE_USER")));
    }
}
