package com.banking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

@Schema(
        name = "RoleChangeApplicationRequest",
        description = "The complete set of service roles the vendor is asking for."
)
public record RoleChangeApplicationRequest(
        @NotNull
        @Schema(
                description = "Desired realm role names. Send the full target set, not a diff.",
                example = "[\"ROLE_VIEWER\"]",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Set<String> requestedRoles
) {
}
