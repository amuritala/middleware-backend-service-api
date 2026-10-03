package com.banking.api.dto;

import com.banking.api.entity.RoleChangeApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Schema(
        name = "RoleChangeApplicationResponse",
        description = "A vendor's request for a new set of service roles, plus its review outcome."
)
public record RoleChangeApplicationResponse(
        @Schema(description = "Application id - use it with the approve/reject endpoints")
        UUID id,

        @Schema(description = "Keycloak user id of the vendor who applied")
        UUID userId,

        @Schema(description = "Username of the vendor who applied", example = "finance@acme-corp.com")
        String username,

        @Schema(description = "Email of the vendor who applied", example = "finance@acme-corp.com")
        String email,

        @Schema(description = "Roles the vendor asked for - the complete target set", example = "[\"ROLE_VIEWER\"]")
        Set<String> requestedRoles,

        @Schema(
                description = "Only the roles the vendor did not already hold at submit time - "
                        + "the part of the request that is new and needs review",
                example = "[\"ROLE_VIEWER\"]"
        )
        Set<String> newlyRequestedRoles,

        @Schema(
                description = "Review status: PENDING (awaiting an administrator), APPROVED (roles granted), "
                        + "REJECTED (declined, roles unchanged).",
                example = "PENDING"
        )
        RoleChangeApplicationStatus status,

        @Schema(description = "Id of the administrator who reviewed it, `null` while pending")
        String reviewedBy,

        @Schema(description = "When it was reviewed, `null` while pending")
        LocalDateTime reviewedAt,

        @Schema(description = "When the vendor submitted it")
        LocalDateTime createdAt
) {
}
