package com.banking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The authorizer's decision on a pending request. The comment is mandatory for the stricter
 * action types (service assignments, suspensions, activations, role assignments) and
 * optional for vendor registration and admin user creation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalDecisionRequest {

    @Size(max = 2000)
    @Schema(description = "Rationale for the decision. Required for most action types.")
    private String comment;
}
