package com.banking.api.controller;

import com.banking.api.dto.LoginRequest;
import com.banking.api.dto.MessageResponse;
import com.banking.api.dto.RefreshTokenRequest;
import com.banking.api.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The three endpoints the portal's sign-in flow needs: exchange credentials for tokens, rotate a
 * refresh token, and revoke the session on sign-out.
 *
 * <p>{@link com.banking.api.config.SecurityConfig} already reserves {@code /api/auth/**} for a
 * stateless chain with no bearer requirement - this controller is what makes that path resolve.
 * Without it every request fell through to the error dispatch and came back as an anonymous 401,
 * which the frontend can only report as "Invalid username or password".</p>
 */
@RestController
@RequestMapping(value = "/api/auth", produces = "application/json")
@RequiredArgsConstructor
@Tag(
        name = "Authentication",
        description = "Sign-in, token refresh and sign-out. These endpoints run without an access " +
                "token - they are how one is obtained in the first place."
)
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Sign in",
            description = "Exchanges a username and password for an access and refresh token. The " +
                    "credentials are verified by Keycloak; a wrong pair is rejected with 401.",
            tags = "Authentication"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token pair for the signed-in user"),
            @ApiResponse(responseCode = "400", description = "Username or password missing"),
            @ApiResponse(responseCode = "401", description = "Wrong username or password")
    })
    @PostMapping("/login")
    public ResponseEntity<String> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.username(), request.password()));
    }

    @Operation(
            summary = "Refresh the session",
            description = "Exchanges a refresh token for a new token pair. Keycloak rotates the " +
                    "refresh token, so the old one stops working after this call. A token the " +
                    "server no longer accepts is reported as 401, which ends the local session.",
            tags = "Authentication"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rotated token pair"),
            @ApiResponse(responseCode = "401", description = "Refresh token expired or revoked")
    })
    @PostMapping("/refresh")
    public ResponseEntity<String> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(
            summary = "Sign out",
            description = "Revokes the refresh token so the Keycloak session ends server-side. " +
                    "Always succeeds: the caller clears its local tokens either way.",
            tags = "Authentication"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session revoked (best effort)")
    })
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.revoke(request.refreshToken());
        return ResponseEntity.ok(new MessageResponse("Signed out"));
    }
}
