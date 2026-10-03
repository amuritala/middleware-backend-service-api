package com.banking.api.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
        name = "ErrorResponse",
        description = "Error envelope returned by every failing endpoint."
)
public class ErrorResponse {

    @Schema(description = "When the error occurred")
    private LocalDateTime timestamp;

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "Short error label", example = "Validation Failed")
    private String error;

    @Schema(description = "Human-readable summary", example = "Invalid request parameters")
    private String message;

    @Schema(
            description = "Present on validation failures (400): each field name mapped to its message. "
                    + "Bind these straight onto the corresponding form inputs.",
            example = "{\"contactEmail\":\"Invalid email format. Please provide a valid email address (e.g., user@example.com)\"}"
    )
    private Map<String, String> details;
}
