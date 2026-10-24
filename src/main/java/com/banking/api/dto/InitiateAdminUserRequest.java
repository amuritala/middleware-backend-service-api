package com.banking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.banking.api.entity.AdminRole;

/**
 * Initiate the creation of a bank staff administrator. The account does not exist until an
 * authorizer approves this request; the temporary password is generated and emailed at that
 * point, never returned through the API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateAdminUserRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 100)
    private String email;

    @NotNull(message = "A governance role is required")
    private AdminRole adminRole;

    /** Optional justification - admin user creation does not require a comment. */
    @Size(max = 2000)
    private String comment;
}
