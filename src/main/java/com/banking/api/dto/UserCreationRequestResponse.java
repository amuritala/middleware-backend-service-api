package com.banking.api.dto;

import com.banking.api.entity.UserCreationKind;
import com.banking.api.entity.UserCreationRequest;
import com.banking.api.entity.UserCreationRequestStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/** Never carries the temporary password. */
public record UserCreationRequestResponse(
        UUID id,
        UserCreationKind kind,
        String companyName,
        java.util.List<String> roles,
        String email,
        String firstName,
        String lastName,
        UserCreationRequestStatus status,
        String requestedBy,
        String reviewedBy,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt
) {
    public static UserCreationRequestResponse from(UserCreationRequest r) {
        return new UserCreationRequestResponse(r.getId(), r.getKind(), r.getCompanyName(),
                r.getRequestedRoles() == null || r.getRequestedRoles().isBlank()
                        ? java.util.List.of() : java.util.List.of(r.getRequestedRoles().split(",")),
                r.getEmail(), r.getFirstName(), r.getLastName(),
                r.getStatus(), r.getRequestedBy(), r.getReviewedBy(), r.getReviewedAt(), r.getCreatedAt());
    }
}
