package com.banking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "UpdateUserRequest",
        description = "Mutable Keycloak user attributes. Omitted fields are left unchanged."
)
public record UpdateUserRequest(
        @Schema(description = "Login name", example = "jane.doe")
        String username,

        @Schema(description = "Email address", example = "jane.doe@acme.com")
        String email,

        @Schema(description = "First name", example = "Jane")
        String firstName,

        @Schema(description = "Last name", example = "Doe")
        String lastName,

        @Schema(description = "Whether the user may sign in", example = "true")
        Boolean enabled,

        @Schema(description = "Whether the email address has been verified", example = "false")
        Boolean emailVerified
) {
}
