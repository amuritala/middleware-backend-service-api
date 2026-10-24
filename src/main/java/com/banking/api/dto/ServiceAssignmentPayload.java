package com.banking.api.dto;

import java.util.List;

/** Stored payload of a SERVICE_ASSIGNMENT request. */
public record ServiceAssignmentPayload(
        String targetKeycloakUserId,
        String targetLabel,
        List<String> roles,
        String mode
) {
}
