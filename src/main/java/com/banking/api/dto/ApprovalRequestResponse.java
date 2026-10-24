package com.banking.api.dto;

import com.banking.api.entity.ApprovalStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * One maker-checker request as the queue renders it. {@code payload} is the parsed JSON
 * snapshot of what was proposed; {@code commentRequired} tells the UI whether the decision
 * box must be filled in before submitting.
 */
@Schema(name = "ApprovalRequestResponse", description = "A maker-checker request awaiting or carrying a decision.")
public record ApprovalRequestResponse(
        UUID id,

        @Schema(example = "VENDOR_REGISTRATION")
        String actionType,

        @Schema(description = "Human-readable action name", example = "Vendor registration")
        String actionLabel,

        boolean commentRequired,

        @Schema(description = "What the request concerns", example = "Acme Corporation Ltd")
        String targetLabel,

        String targetId,

        @Schema(example = "PENDING")
        ApprovalStatus status,

        @Schema(description = "Parsed snapshot of the proposed action")
        Map<String, Object> payload,

        @Schema(description = "Keycloak user id of the initiator - compare it with your own to spot your own requests")
        String initiatedByKeycloakUserId,

        String initiatedByName,
        String initiatedByEmail,

        @Schema(example = "INITIATOR")
        String initiatedByRole,

        String initiatedComment,

        String authorizedByKeycloakUserId,
        String authorizedByName,

        @Schema(example = "AUTHORIZER")
        String authorizedByRole,

        String authorizationComment,
        LocalDateTime authorizedAt,
        LocalDateTime createdAt
) {
}
