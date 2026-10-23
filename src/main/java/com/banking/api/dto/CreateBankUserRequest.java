package com.banking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
        name = "CreateBankUserRequest",
        description = "Payload for creating a bank user (staff administrator). The account gets ROLE_ADMIN "
                + "and must change the temporary password at first sign-in."
)
public class CreateBankUserRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email is invalid")
    @Size(max = 100, message = "Email must be at most 100 characters")
    @Schema(description = "Work email. Also becomes the login username, so it must be unique.",
            example = "jane.doe@bank.com")
    private String email;

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must be at most 100 characters")
    @Schema(description = "First name.", example = "Jane")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must be at most 100 characters")
    @Schema(description = "Last name.", example = "Doe")
    private String lastName;

    @NotBlank(message = "Password is required")
    @Schema(description = "Temporary password, emailed to the user and replaced at first sign-in.",
            example = "S3cure!pass")
    private String password;
}
