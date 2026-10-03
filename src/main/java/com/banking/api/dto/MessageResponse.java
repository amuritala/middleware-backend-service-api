package com.banking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "MessageResponse",
        description = "A simple acknowledgement with a human-readable message."
)
public record MessageResponse(
        @Schema(description = "What happened.", example = "Password updated successfully")
        String message
) {
}
