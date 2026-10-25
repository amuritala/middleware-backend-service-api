package com.banking.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(
        name = "RefreshTokenRequest",
        description = "Carries the refresh token for the two calls that act on an existing session: " +
                "exchanging it for a fresh access token, and revoking it on sign-out."
)
public record RefreshTokenRequest(

        @NotBlank(message = "Refresh token is required")
        @JsonProperty("refresh_token")
        @Schema(description = "The refresh token issued at sign-in.", example = "eyJhbGciOi...")
        String refreshToken
) {
}
