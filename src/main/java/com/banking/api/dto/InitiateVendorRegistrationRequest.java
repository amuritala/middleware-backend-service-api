package com.banking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Initiate a vendor registration on behalf of a company. No password travels with this
 * request: the credential is generated and emailed when an authorizer approves, and the
 * Keycloak account created at this stage is disabled - the vendor cannot sign in until then.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "InitiateVendorRegistrationRequest",
        description = "Payload for an administrator-initiated vendor registration.")
public class InitiateVendorRegistrationRequest {

    // === Contact Information ===

    @NotBlank(message = "Contact email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 100)
    private String contactEmail;

    @NotBlank(message = "Contact first name is required")
    @Size(max = 100)
    private String contactFirstName;

    @NotBlank(message = "Contact last name is required")
    @Size(max = 100)
    private String contactLastName;

    @NotBlank(message = "Contact phone is required")
    @Pattern(regexp = "^\\+?[1-9][0-9]{1,14}$|^[0-9\\s\\-\\(\\)]{7,20}$",
            message = "Enter a valid contact phone number")
    @Size(max = 20)
    private String contactPhone;

    // === Company Information ===

    @NotBlank(message = "Company name is required")
    @Size(max = 100)
    private String companyName;

    @NotBlank(message = "Registration number is required")
    @Size(max = 50)
    private String registrationNumber;

    @Size(max = 100)
    private String country;

    @NotBlank(message = "Tax ID is required")
    @Size(max = 50)
    private String taxId;

    @NotBlank(message = "Company address is required")
    @Size(max = 255)
    private String companyAddress;

    @NotBlank(message = "Industry is required")
    @Size(max = 50)
    private String industry;

    @NotBlank(message = "Company phone is required")
    @Pattern(regexp = "^\\+?[1-9][0-9]{1,14}$|^[0-9\\s\\-\\(\\)]{7,20}$",
            message = "Enter a valid company phone number")
    @Size(max = 20)
    private String phoneNumber;

    /** Optional justification - vendor registration does not require a comment. */
    @Size(max = 2000)
    private String comment;
}
