package com.banking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Initiate suspending or re-activating a vendor. The state change only happens once an
 * authorizer signs it off.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateClientActionRequest {

    @NotNull(message = "Client id is required")
    private UUID clientId;

    @Size(max = 50)
    @Schema(description = "Service roles to (re)grant on activation. Ignored for suspensions.")
    private List<String> roles;

    @NotBlank(message = "A comment is required")
    @Size(max = 2000)
    private String comment;
}
