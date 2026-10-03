package com.banking.api.dto;

import com.banking.api.validation.PasswordValidator;
import jakarta.persistence.Column;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
        name = "RegisterRequest",
        description = "Payload for the public sign-up form. All fields are required except `country`."
)
public class RegisterRequest {

    // === Contact Information ===

    @NotBlank(message = "{validation.contactEmail.required}")
    @Email(message = "{validation.contactEmail.invalid}")
    @Size(max = 100, message = "{validation.contactEmail.size}")
    @Schema(
            description = "Contact email. Also becomes the Keycloak username, so it must be unique.",
            example = "finance@acme-corp.com"
    )
    private String contactEmail;


    @NotBlank(message = "{validation.contactPerson.required}")
    @Size(max = 100, message = "{validation.contactPerson.size}")
    @Schema(description = "First name of the contact person.", example = "Jane")
    private String contactFirstName;

    @NotBlank(message = "{validation.contactPerson.required}")
    @Size(max = 100, message = "{validation.contactPerson.size}")
    @Schema(description = "Last name of the contact person.", example = "Doe")
    private String contactLastName;

    @NotBlank(message = "{validation.contactPhone.required}")
    @Pattern(
            regexp = "^\\+?[1-9][0-9]{1,14}$|^[0-9\\s\\-\\(\\)]{7,20}$",
            message = "{validation.contactPhone.invalid}"
    )
    @Size(max = 20, message = "{validation.contactPhone.size}")
    @Schema(
            description = "Contact phone number. Accepts an international format (`+2348012345678`) or "
                    + "spaced / hyphenated digits (`0801 234 5678`).",
            example = "+2348012345678"
    )
    private String contactPhone;

    // === Company Information ===

    @NotBlank(message = "{validation.companyName.required}")
    @Size(max = 100, message = "{validation.companyName.size}")
    @Schema(description = "Legal name of the company.", example = "Acme Corporation Ltd")
    private String companyName;

    @NotBlank(message = "{validation.registrationNumber.required}")
    @Size(max = 50, message = "{validation.registrationNumber.size}")
    @Schema(description = "Company registration / CAC number.", example = "RC-1234567")
    private String registrationNumber;

    //@NotBlank(message = "{validation.country.required}")
    //@Size(max = 100, message = "{validation.country.size}")
    @Schema(description = "Country of operation. Optional - not currently validated.", example = "Nigeria")
    private String country;

//    @NotBlank(message = "{validation.address.required}")
//    @Size(max = 255, message = "{validation.address.size}")
//    private String address;

    @NotBlank(message = "{validation.taxId.required}")
    @Size(max = 50, message = "{validation.taxId.size}")
    @Schema(description = "Tax identification number.", example = "TIN-98765432")
    private String taxId;

    @NotBlank(message = "{validation.companyAddress.required}")
    @Size(max = 255, message = "{validation.companyAddress.size}")
    @Schema(description = "Registered company address.", example = "14 Marina Road, Lagos Island, Lagos")
    private String companyAddress;

    @NotBlank(message = "{validation.industry.required}")
    @Size(max = 50, message = "{validation.industry.size}")
    @Schema(description = "Industry the company operates in.", example = "Financial Services")
    private String industry;

    @NotBlank(message = "{validation.phoneNumber.required}")
    @Pattern(
            regexp = "^\\+?[1-9][0-9]{1,14}$|^[0-9\\s\\-\\(\\)]{7,20}$",
            message = "{validation.phoneNumber.invalid}"
    )
    @Size(max = 20, message = "{validation.phoneNumber.size}")
    @Schema(
            description = "Company phone number. Same accepted formats as the contact phone.",
            example = "+2348098765432"
    )
    private String phoneNumber;

    @NotBlank(message = "Password is required")
//    @PasswordValidator(
//            minLength = 8,
//            maxLength = 100
//            requireDigit = true,
//            requireLowercase = true,
//            requireUppercase = true,
//            requireSpecialChar = true
//    )
    @Schema(
            description = "Account password. The server only checks that it is not blank - strength rules are "
                    + "left to the client.",
            example = "S3cure!pass"
    )
    private String password;
}