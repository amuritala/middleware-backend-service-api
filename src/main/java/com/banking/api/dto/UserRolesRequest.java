package com.banking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(
        name = "UserRolesRequest",
        description = "Realm roles to grant to or revoke from a user, with the mandatory justification - "
                + "role changes travel through the maker-checker, so an authorizer reviews them before they apply."
)
public record UserRolesRequest(
        @NotEmpty
        @Schema(
                description = "Realm role names. At least one is required; protected roles (ROLE_ADMIN, "
                        + "ROLE_CLIENT) are refused.",
                example = "[\"ROLE_VIEWER\"]",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        List<String> roles,

        @NotBlank(message = "A comment is required for service assignments")
        @Schema(
                description = "Why these services are being changed. Required.",
                example = "Treasury integration requested by the vendor's ops lead.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String comment
) {
}
