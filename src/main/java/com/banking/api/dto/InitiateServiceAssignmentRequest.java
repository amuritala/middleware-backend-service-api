package com.banking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Initiate granting or revoking platform service roles on a user (typically a vendor's
 * contact). The whole target set travels with the request so the approval screen can show
 * exactly what will change.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateServiceAssignmentRequest {

    @NotBlank(message = "Target user is required")
    @Schema(description = "Keycloak user id (the JWT `sub` claim) of the user being re-roled.")
    private String targetKeycloakUserId;

    @Size(max = 200)
    @Schema(description = "Display label for the approval queue, e.g. the vendor contact's email.")
    private String targetLabel;

    @NotEmpty(message = "At least one service role is required")
    @Schema(description = "Service roles to grant or revoke.", example = "[\"ROLE_VIEWER\"]")
    private List<String> roles;

    @NotBlank(message = "Mode is required")
    @Schema(description = "GRANT to add the roles, REVOKE to remove them.", example = "GRANT")
    private String mode;

    @NotBlank(message = "A comment is required for service assignments")
    @Size(max = 2000)
    private String comment;
}
