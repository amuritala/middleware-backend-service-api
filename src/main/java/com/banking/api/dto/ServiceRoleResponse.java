package com.banking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "ServiceRoleResponse",
        description = "One requestable platform service. Every service is a Keycloak realm role "
                + "other than the system roles, which are never listed."
)
public record ServiceRoleResponse(
        @Schema(description = "Realm role name - send this value in `requestedRoles`",
                example = "ROLE_VIEW_ACCOUNT_STATEMENT")
        String name,

        @Schema(description = "Human readable description maintained on the role in Keycloak. "
                + "Empty string when the role has none.",
                example = "View account statement role")
        String description
) {
}
