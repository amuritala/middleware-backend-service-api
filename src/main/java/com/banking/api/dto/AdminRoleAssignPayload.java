package com.banking.api.dto;

import com.banking.api.entity.AdminRole;

/** Stored payload of an ADMIN_ROLE_ASSIGN request. */
public record AdminRoleAssignPayload(
        String targetKeycloakUserId,
        String targetLabel,
        AdminRole adminRole
) {
}
