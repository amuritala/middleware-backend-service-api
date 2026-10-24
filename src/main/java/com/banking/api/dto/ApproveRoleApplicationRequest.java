package com.banking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

@Schema(
        name = "ApproveRoleApplicationRequest",
        description = "The subset of the vendor's requested service roles to grant on approval. "
                + "Omit the body entirely to grant every requested role."
)
public record ApproveRoleApplicationRequest(
        @Schema(
                description = "Service roles to grant - must be a non-empty subset of the application's "
                        + "requestedRoles. Roles the admin leaves out are simply never granted.",
                example = "[\"ROLE_VIEWER\"]"
        )
        Set<String> approvedRoles
) {
}
