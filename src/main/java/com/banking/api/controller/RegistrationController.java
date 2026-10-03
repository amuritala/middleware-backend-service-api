package com.banking.api.controller;

import com.banking.api.dto.AvailabilityResponse;
import com.banking.api.dto.ClientResponse;
import com.banking.api.dto.RegisterRequest;
import com.banking.api.exception.ErrorResponse;
import com.banking.api.service.PublicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api", produces = "application/json")
@RequiredArgsConstructor
@Slf4j
public class RegistrationController {

    private final PublicService publicService;

    @Operation(
            summary = "Register a new client",
            description = "Creates the Keycloak user and a PENDING client record. Public - no token required. "
                    + "The account cannot sign in as a vendor until an administrator approves it.",
            tags = "Registration"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Client registered and awaiting approval",
                    content = @Content(schema = @Schema(implementation = ClientResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed - `details` maps each field name to its message",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "A user with this email address already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Registration failed while creating the Keycloak user",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/register")
    public ResponseEntity<ClientResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Public registration request for: {}", request.getCompanyName());
        ClientResponse response = publicService.registerClient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Check username / email availability",
            description = "Tells the sign-up form whether a username and an email are still free. Omit a parameter "
                    + "to skip that check; its `*Available` flag is then reported as `true`. Registration uses the "
                    + "contact email as the username, so both flags normally describe the same value.",
            tags = "Registration"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Availability of the supplied values",
                    content = @Content(schema = @Schema(implementation = AvailabilityResponse.class))
            )
    })
    @GetMapping("/check-availability")
    public ResponseEntity<AvailabilityResponse> checkAvailability(
            @Parameter(description = "Username to test. Registration sets the username to the contact email.")
            @RequestParam(required = false) String username,
            @Parameter(description = "Email address to test against existing users.")
            @RequestParam(required = false) String email) {
        return ResponseEntity.ok(publicService.checkAvailability(username, email));
    }
}
