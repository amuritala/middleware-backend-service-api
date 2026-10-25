package com.banking.api.controller;

import com.banking.api.config.SecurityConfig;
import com.banking.api.dto.AuthResponse;
import com.banking.api.exception.GlobalExceptionHandler;
import com.banking.api.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
// The application class enables JPA auditing, so scope the context to the web layer explicitly.
@ContextConfiguration(classes = {AuthController.class, SecurityConfig.class, GlobalExceptionHandler.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private com.banking.api.service.AuthAuditService auditService;

    @MockitoBean
    private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @Test
    void mapsServiceStatusErrorsInsteadOfReturningServerError() throws Exception {
        when(authService.refresh(any())).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"expired\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"message\":\"Refresh token is invalid or expired\"}"));
    }

    @Test
    void allowsAnonymousLoginRequestThroughSecurity() throws Exception {
        when(authService.login(any()))
                .thenReturn(new AuthResponse("access-token", "refresh-token"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer stale-or-invalid-token")
                        .content("""
                                {"username":"customer","password":"secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "access_token": "access-token",
                          "refresh_token": "refresh-token"
                        }
                        """));

        verify(authService).login(any());
    }

    @Test
    void allowsAnonymousRefreshRequestThroughSecurity() throws Exception {
        when(authService.refresh(any()))
                .thenReturn(new AuthResponse("new-access", "new-refresh"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refresh_token":"old-refresh"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"access_token": "new-access", "refresh_token": "new-refresh"}
                        """));

        verify(authService).refresh(any());
    }

    @Test
    void allowsAnonymousLogoutRequestThroughSecurity() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refresh_token":"old-refresh"}
                                """))
                .andExpect(status().isNoContent());

        verify(authService).logout(any());
    }

    @Test
    void logsFailedLoginWithUsernameWithoutPassword() throws Exception {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AuthController.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            when(authService.login(any())).thenThrow(new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid username or password"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", "203.0.113.9")
                            .content("{\"username\":\"jane@acme.com\",\"password\":\"TopSecret!\"}"))
                    .andExpect(status().isUnauthorized());

            String logged = appender.list.get(0).getFormattedMessage();
            org.junit.jupiter.api.Assertions.assertTrue(logged.contains("Login failed"));
            org.junit.jupiter.api.Assertions.assertTrue(logged.contains("user=jane@acme.com"));
            org.junit.jupiter.api.Assertions.assertTrue(logged.contains("status=401"));
            org.junit.jupiter.api.Assertions.assertTrue(logged.contains("forwardedFor=203.0.113.9"));
            org.junit.jupiter.api.Assertions.assertFalse(logged.contains("TopSecret!"));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
