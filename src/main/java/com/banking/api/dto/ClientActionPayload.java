package com.banking.api.dto;

import java.util.List;
import java.util.UUID;

/** Stored payload of a CLIENT_SUSPEND / CLIENT_ACTIVATE request. */
public record ClientActionPayload(
        UUID clientId,
        String companyName,
        List<String> roles
) {
}
