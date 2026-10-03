package com.banking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(
        name = "DownstreamAuditResponse",
        description = "One recorded call this service made to a downstream system on behalf of a user."
)
public record DownstreamAuditResponse(
        @Schema(description = "Audit record id")
        UUID id,

        @Schema(description = "Id of the user the call was made for", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        String userId,

        @Schema(description = "Service operation performed by the orchestrator", example = "queryCustomer")
        String operation,

        @Schema(description = "Serialized request payload")
        String request,

        @Schema(description = "Serialized response payload")
        String response,

        @Schema(description = "HTTP status the downstream system replied with", example = "200")
        int httpStatus,

        @Schema(description = "When the call started")
        LocalDateTime startTime,

        @Schema(description = "When the call finished")
        LocalDateTime endTime
) {
}
