package com.banking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(
        name = "UserRolesRequest",
        description = "Realm roles to grant to or revoke from a user."
)
public record UserRolesRequest(
        @NotEmpty
        @Schema(
                description = "Realm role names. At least one is required; protected roles (ROLE_ADMIN, "
                        + "ROLE_CLIENT) are refused.",
                example = "[\"ROLE_VIEWER\"]",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        List<String> roles
) {
}
