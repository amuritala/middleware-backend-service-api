package com.banking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(
        name = "ApproveClientRequest",
        description = "Service roles to grant to the client's user when the client is approved or activated."
)
public record ApproveClientRequest(
        @Schema(
                description = "Realm role names taken from `GET /api/admin/roles`. ROLE_CLIENT is added by the "
                        + "server automatically.",
                example = "[\"ROLE_VIEWER\"]"
        )
        List<String> approvedRoles
) {
}



