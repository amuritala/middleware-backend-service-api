package com.banking.api.controller;

import com.banking.api.dto.AuthRequest;
import com.banking.api.dto.AuthResponse;
import com.banking.api.dto.RefreshTokenRequest;
import com.banking.api.service.AuthAuditService;
import com.banking.api.service.AuthService;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Every outcome is logged with the username and client address so a failed sign-in can be
 * traced. Passwords and tokens are never logged.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final AuthAuditService auditService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request, HttpServletRequest http) {
        String user = sanitize(request.getUsername());
        String client = clientInfo(http);
        LocalDateTime start = LocalDateTime.now();
        ObjectNode auditRequest = node()
                .put("username", request.getUsername()).put("password", request.getPassword());
        try {
            AuthResponse response = authService.login(request);
            log.info("Login succeeded: user={} {}", user, client);
            auditService.record("login", request.getUsername(), auditRequest, tokens(response), 200, start);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            log.warn("Login failed: user={} status={} reason=\"{}\" {}",
                    user, e.getStatusCode().value(), sanitize(e.getReason()), client);
            auditService.record("login", request.getUsername(), auditRequest, failure(e),
                    e.getStatusCode().value(), start);
            throw e;
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                                HttpServletRequest http) {
        String client = clientInfo(http);
        LocalDateTime start = LocalDateTime.now();
        ObjectNode auditRequest = node().put("refresh_token", request.getRefreshToken());
        try {
            AuthResponse response = authService.refresh(request);
            log.debug("Token refresh succeeded: {}", client);
            auditService.record("refresh", null, auditRequest, tokens(response), 200, start);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            log.warn("Token refresh failed: status={} reason=\"{}\" {}",
                    e.getStatusCode().value(), sanitize(e.getReason()), client);
            auditService.record("refresh", null, auditRequest, failure(e), e.getStatusCode().value(), start);
            throw e;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest http) {
        String client = clientInfo(http);
        LocalDateTime start = LocalDateTime.now();
        ObjectNode auditRequest = node().put("refresh_token", request.getRefreshToken());
        try {
            authService.logout(request);
            log.info("Logout completed: {}", client);
            auditService.record("logout", null, auditRequest, null, 204, start);
            return ResponseEntity.noContent().build();
        } catch (ResponseStatusException e) {
            log.warn("Logout failed: status={} reason=\"{}\" {}",
                    e.getStatusCode().value(), sanitize(e.getReason()), client);
            auditService.record("logout", null, auditRequest, failure(e), e.getStatusCode().value(), start);
            throw e;
        }
    }

    private static ObjectNode node() {
        return JsonNodeFactory.instance.objectNode();
    }

    private static ObjectNode tokens(AuthResponse response) {
        return node()
                .put("access_token", response.getAccessToken())
                .put("refresh_token", response.getRefreshToken());
    }

    private static ObjectNode failure(ResponseStatusException e) {
        return node().put("message", e.getReason());
    }

    private static String clientInfo(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        return "remoteAddr=" + http.getRemoteAddr()
                + (forwarded == null ? "" : " forwardedFor=" + sanitize(forwarded));
    }

    /** Keeps client-supplied text on one line and bounded, so it cannot forge log entries. */
    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "n/a";
        }
        String singleLine = value.replaceAll("[\\r\\n\\t\\p{Cntrl}]", " ").trim();
        return singleLine.length() > 200 ? singleLine.substring(0, 200) : singleLine;
    }
}
