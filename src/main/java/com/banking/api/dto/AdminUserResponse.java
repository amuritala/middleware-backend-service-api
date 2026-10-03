package com.banking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "AdminUserResponse", description = "A Keycloak user as seen by an administrator.")
public record AdminUserResponse(
        @Schema(description = "Keycloak user id - the JWT `sub` claim", example = "0f8fad5b-d9cb-469f-a165-70867728950e")
        String keycloakUserId,

        @Schema(description = "Login name", example = "jane.doe")
        String username,

        @Schema(description = "Email address", example = "jane.doe@acme.com")
        String email,

        @Schema(description = "First name", example = "Jane")
        String firstName,

        @Schema(description = "Last name", example = "Doe")
        String lastName,

        @Schema(description = "Whether the user may sign in", example = "true")
        boolean enabled,

        @Schema(description = "Whether the email address has been verified", example = "false")
        boolean emailVerified,

        @Schema(description = "Realm roles currently assigned", example = "[\"ROLE_CLIENT\",\"ROLE_VIEWER\"]")
        List<String> roles
) {
}
