package com.banking.api.dto;

import com.banking.api.entity.AdminRole;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The signed-in administrator as the governance layer sees them. The frontend fetches this
 * once per page load and derives every screen, button and route from {@code adminRole} and
 * {@code permissions} - the roles live in the local database, not in the token.
 */
@Schema(name = "AdminMeResponse", description = "Governance profile of the signed-in administrator.")
public record AdminMeResponse(
        String keycloakUserId,
        String firstName,
        String lastName,
        String fullName,
        String email,

        @Schema(description = "Null when the account predates the governance rollout and has not been classified yet")
        AdminRole adminRole,

        boolean mustChangePassword,

        @Schema(description = "Capability keys the UI maps to screens and buttons",
                example = "[\"initiate.vendor_registration\", \"authorize\"]")
        List<String> permissions
) {
}
