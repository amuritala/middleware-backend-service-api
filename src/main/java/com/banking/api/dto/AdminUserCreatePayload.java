package com.banking.api.dto;

import com.banking.api.entity.AdminRole;

/** Stored payload of an ADMIN_USER_CREATE request - rendered on the approval screen. */
public record AdminUserCreatePayload(
        String firstName,
        String lastName,
        String email,
        AdminRole adminRole,
        String userId
) {
}
