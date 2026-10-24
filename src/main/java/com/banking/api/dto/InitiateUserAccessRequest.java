package com.banking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Initiate a suspend, reinstatement or time-lock on an account. Every one of these travels
 * through the maker-checker queue: Control proposes it with a comment, an authorizer signs it
 * off with a comment of their own before anything happens to the account.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateUserAccessRequest {

    /** The access action being proposed. */
    public enum Action {
        /** Block sign-in and API access until an approved reinstatement. */
        SUSPEND,
        /** Lift a suspension; the account may sign in again. */
        UNSUSPEND,
        /** Block sign-in and API access until the requested number of minutes passes. */
        LOCK
    }

    @NotNull(message = "The action is required")
    private Action action;

    @NotBlank(message = "Target user is required")
    @Schema(description = "Keycloak user id (the JWT `sub` claim) of the account being acted on.")
    private String targetKeycloakUserId;

    @Schema(description = "Label for the target shown in the approvals queue and audit trail "
            + "(usually their email). Falls back to the stored user record when blank.")
    private String targetLabel;

    @Schema(description = "LOCK only: how many minutes the lock lasts. Required for LOCK, "
            + "ignored otherwise.", example = "30")
    private Integer lockMinutes;

    @NotBlank(message = "A comment is required for user access actions")
    @Size(max = 2000)
    private String comment;
}
