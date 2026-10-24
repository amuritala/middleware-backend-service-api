package com.banking.api.dto;

import com.banking.api.entity.AdminRole;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** One bank staff administrator as the user-management screen lists them. */
@Schema(name = "AdminUserSummaryResponse", description = "A bank staff administrator with a governance role.")
public record AdminUserSummaryResponse(
        String keycloakUserId,
        String firstName,
        String lastName,
        String fullName,
        String email,

        @Schema(example = "INITIATOR")
        AdminRole adminRole,

        boolean enabled,
        boolean mustChangePassword,

        @Schema(description = "Suspended by an approved maker-checker request - cannot sign in "
                + "and every API call from its token is refused until reinstated.")
        boolean suspended,

        @Schema(description = "When an approved time lock expires; null when no lock is in force.")
        LocalDateTime lockedUntil,

        LocalDateTime lastLogin,
        LocalDateTime createdAt
) {
}
