package com.banking.api.controller;

import com.banking.api.dto.ChangePasswordRequest;
import com.banking.api.dto.MessageResponse;
import com.banking.api.exception.ErrorResponse;
import com.banking.api.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service account endpoints for any signed-in user - vendor or admin alike.
 */
@RestController
@RequestMapping(value = "/api/account", produces = "application/json")
@RequiredArgsConstructor
@SecurityRequirement(name = "keycloak")
@Tag(
        name = "Account",
        description = "Self-service operations on the signed-in user's own account. Available to every "
                + "authenticated user, vendor or administrator."
)
public class AccountController {

    private final AccountService accountService;

    @Operation(
            summary = "Change my password",
            description = "Changes the signed-in user's own password. The current password is re-verified before "
                    + "anything is written - a wrong current password is rejected with 401 and the password stays "
                    + "unchanged. A user id can never be supplied: the target account is always the one the "
                    + "access token belongs to.",
            tags = "Account"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Password changed",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The new password fails the strength policy (8-100 chars with upper, lower, "
                            + "digit and special character)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing/expired token, or the current password is wrong - nothing is changed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PutMapping("/password")
    public ResponseEntity<?> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("JWT authentication is required");
        }
        return accountService.changePassword(jwt, request);
    }
}
