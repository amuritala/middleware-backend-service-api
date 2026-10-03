package com.banking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
        name = "AvailabilityResponse",
        description = "Whether the requested username and email are still free to register."
)
public class AvailabilityResponse {

    @Schema(description = "`false` when the username is already taken.", example = "true")
    private boolean usernameAvailable;

    @Schema(description = "`false` when the email is already registered.", example = "true")
    private boolean emailAvailable;
}