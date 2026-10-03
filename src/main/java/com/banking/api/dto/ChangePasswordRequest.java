package com.banking.api.dto;

import com.banking.api.validation.PasswordValidator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(
        name = "ChangePasswordRequest",
        description = "Payload for changing the signed-in user's own password. The current password must be "
                + "supplied and is verified before the new one is applied."
)
public record ChangePasswordRequest(

        @NotBlank(message = "Current password is required")
        @Schema(description = "The password the user signs in with today.", example = "OldP@ssw0rd")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @PasswordValidator(minLength = 8, maxLength = 100)
        @Schema(
                description = "The replacement password. Must be 8-100 characters with at least one digit, "
                        + "one lowercase letter, one uppercase letter and one special character.",
                example = "N3wS3cure!pass"
        )
        String newPassword
) {
}
