package com.banking.api.dto;

import com.banking.api.entity.AdminRole;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Initiate a governance-role change for an existing bank staff account. Role assignment is
 * itself a maker-checker action: control proposes, an authorizer decides.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateRoleAssignmentRequest {

    @NotBlank(message = "Target user is required")
    @Schema(description = "Keycloak user id (the JWT `sub` claim) of the administrator being re-roled.")
    private String targetKeycloakUserId;

    @NotNull(message = "The new governance role is required")
    private AdminRole adminRole;

    @NotBlank(message = "A comment is required for role assignments")
    @Size(max = 2000)
    private String comment;
}
