package com.banking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/** One governance audit entry: what happened, who did it, and why. */
@Schema(name = "AuditLogResponse", description = "An immutable audit record of a governance action.")
public record AuditLogResponse(
        UUID id,

        @Schema(example = "VENDOR_REGISTRATION")
        String actionType,

        @Schema(example = "APPROVED")
        String outcome,

        String summary,
        String detail,
        String targetLabel,

        String actorName,
        String actorEmail,

        @Schema(example = "AUTHORIZER")
        String actorRole,

        String comment,
        LocalDateTime createdAt
) {
}
