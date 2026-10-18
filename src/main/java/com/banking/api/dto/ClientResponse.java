package com.banking.api.dto;

import com.banking.api.entity.ClientStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ClientResponse", description = "A client (vendor) on the platform.")
public class ClientResponse {

    @Schema(description = "Client id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;
    @Schema(description = "Contact email of the client", example = "finance@acme-corp.com")
    private String contactEmail;
    @Schema(description = "Legal name of the company", example = "Acme Corporation Ltd")
    private String companyName;
    @Schema(description = "Company registration / CAC number", example = "RC-1234567")
    private String registrationNumber;
    @Schema(description = "First name of the contact person", example = "Jane")
    private String contactFirstName;
    @Schema(description = "Last name of the contact person", example = "Doe")
    private String contactLastName;
    @Schema(description = "Contact phone number", example = "+2348012345678")
    private String contactPhone;
    @Schema(description = "Country of operation", example = "Nigeria")
    private String country;
    @Schema(description = "Registered company address", example = "14 Marina Road, Lagos Island, Lagos")
    private String address;
    @Schema(description = "Tax identification number", example = "TIN-98765432")
    private String taxId;
    @Schema(description = "Company address supplied at registration", example = "14 Marina Road, Lagos Island, Lagos")
    private String companyAddress;
    @Schema(description = "Industry the company operates in", example = "Financial Services")
    private String industry;
    @Schema(description = "Company phone number", example = "+2348012345678")
    private String phoneNumber;
    @Schema(
            description = "Onboarding status: PENDING (awaiting review), APPROVED or REJECTED (review outcome), "
                    + "ACTIVE (live), SUSPENDED (blocked, API keys revoked), INACTIVE.",
            example = "PENDING"
    )
    private ClientStatus status;
    @Schema(description = "When the client registered")
    private LocalDateTime createdAt;
    @Schema(description = "When the client was approved, `null` while still pending")
    private LocalDateTime approvedAt;
    @Schema(description = "Number of users belonging to this client", example = "3")
    private Integer userCount;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Only returned when an admin creates the account: whether the welcome email with the "
            + "temporary password was sent. When false, give the user the password another way.", example = "true")
    private Boolean welcomeEmailSent;
}
