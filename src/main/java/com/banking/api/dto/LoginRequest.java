package com.banking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(
        name = "LoginRequest",
        description = "Username/password pair exchanged for a Keycloak token pair. The password never " +
                "leaves Keycloak: this API only forwards the grant."
)
public record LoginRequest(

        @NotBlank(message = "Username is required")
        @Schema(description = "Keycloak username or email.", example = "admin@bank.com")
        String username,

        @NotBlank(message = "Password is required")
        @Schema(description = "The password the user signs in with.", example = "S3cret!pass")
        String password
) {
}
