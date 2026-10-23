package com.banking.api.service;

import com.banking.api.dto.AuthRequest;
import com.banking.api.dto.AuthResponse;
import com.banking.api.dto.RefreshTokenRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthServiceTest {

    @Test
    void exchangesCredentialsForKeycloakAccessAndRefreshTokens() {
        WebClient.Builder webClientBuilder = WebClient.builder()
                .exchangeFunction(request -> {
                    assertEquals("/realms/banking/protocol/openid-connect/token",
                            request.url().getPath());
                    assertEquals(MediaType.APPLICATION_FORM_URLENCODED,
                            request.headers().getContentType());
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", "application/json")
                            .body("""
                                    {
                                      "access_token": "access-value",
                                      "refresh_token": "refresh-value",
                                      "expires_in": 300
                                    }
                                    """)
                            .build());
                });
        AuthService service = new AuthService(
                        webClientBuilder, new ObjectMapper(),
                        "http://keycloak", "banking", "banking-service-api", "client-secret");
        AuthRequest request = new AuthRequest();
        request.setUsername("customer");
        request.setPassword("password");

        AuthResponse response = service.login(request);

        assertEquals("access-value", response.getAccessToken());
        assertEquals("refresh-value", response.getRefreshToken());
    }

    @Test
    void returnsUnauthorizedWhenKeycloakRejectsCredentials() {
        AuthService service = new AuthService(
                WebClient.builder().exchangeFunction(request -> Mono.just(
                        ClientResponse.create(HttpStatus.UNAUTHORIZED)
                                .header("Content-Type", "application/json")
                                .body("{\"error\":\"invalid_grant\",\"error_description\":\"Invalid user credentials\"}")
                                .build())),
                new ObjectMapper(), "http://keycloak", "banking", "banking-service-api", "client-secret");
        AuthRequest request = new AuthRequest();
        request.setUsername("customer");
        request.setPassword("incorrect");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.login(request));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Invalid username or password", exception.getReason());
    }

    @Test
    void mapsKeycloakClientConfigurationErrorsToBadGateway() {
        Logger logger = (Logger) LoggerFactory.getLogger(AuthService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        AuthService service = new AuthService(
                WebClient.builder().exchangeFunction(request -> Mono.just(
                        ClientResponse.create(HttpStatus.BAD_REQUEST)
                                .header("Content-Type", "application/json")
                                .body("""
                                        {
                                          "error":"unauthorized_client",
                                          "error_description":"Client not permitted to use direct access grants\\nCheck client settings"
                                        }
                                        """)
                                .build())),
                new ObjectMapper(), "http://keycloak", "banking", "banking-service-api", "client-secret");
        AuthRequest request = new AuthRequest();
        request.setUsername("customer");
        request.setPassword("password-value");

        try {
            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class, () -> service.login(request));

            assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
            assertEquals(
                    "Keycloak rejected the token request; check client configuration and direct access grants",
                    exception.getReason());

            String loggedMessages = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
            assertTrue(loggedMessages.contains("unauthorized_client"));
            assertTrue(loggedMessages.contains(
                    "Client not permitted to use direct access grants Check client settings"));
            assertTrue(loggedMessages.contains("realm=banking"));
            assertTrue(loggedMessages.contains("clientId=banking-service-api"));
            assertTrue(loggedMessages.contains("clientSecretConfigured=true"));
            assertFalse(loggedMessages.contains("client-secret"));
            assertFalse(loggedMessages.contains("password-value"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void rejectsMissingCredentialsBeforeCallingKeycloak() {
        AuthService service = new AuthService(
                WebClient.builder().exchangeFunction(request -> {
                    throw new AssertionError("Keycloak must not be called without credentials");
                }),
                new ObjectMapper(), "http://keycloak", "banking", "banking-service-api", "");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.login(new AuthRequest()));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void mapsKeycloakServerErrorsToBadGateway() {
        AuthService service = new AuthService(
                WebClient.builder().exchangeFunction(request -> Mono.just(
                        ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                                .header("Content-Type", "application/json")
                                .body("{\"error\":\"temporarily_unavailable\"}")
                                .build())),
                new ObjectMapper(), "http://keycloak", "banking", "banking-service-api", "");
        AuthRequest request = new AuthRequest();
        request.setUsername("customer");
        request.setPassword("password");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.login(request));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
    }

    @Test
    void rejectsIncompleteTokenResponseAsBadGateway() {
        AuthService service = new AuthService(
                WebClient.builder().exchangeFunction(request -> Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                                .header("Content-Type", "application/json")
                                .body("{\"access_token\":\"access-value\"}")
                                .build())),
                new ObjectMapper(), "http://keycloak", "banking", "banking-service-api", "");
        AuthRequest request = new AuthRequest();
        request.setUsername("customer");
        request.setPassword("password");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.login(request));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
    }

    @Test
    void exchangesRefreshTokenForNewTokens() {
        WebClient.Builder webClientBuilder = WebClient.builder()
                .exchangeFunction(request -> {
                    assertEquals("/realms/banking/protocol/openid-connect/token",
                            request.url().getPath());
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", "application/json")
                            .body("""
                                    {"access_token": "new-access", "refresh_token": "new-refresh"}
                                    """)
                            .build());
                });
        AuthService service = new AuthService(
                webClientBuilder, new ObjectMapper(),
                "http://keycloak", "banking", "banking-service-api", "client-secret");
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("old-refresh");

        AuthResponse response = service.refresh(request);

        assertEquals("new-access", response.getAccessToken());
        assertEquals("new-refresh", response.getRefreshToken());
    }

    @Test
    void returnsUnauthorizedWhenRefreshTokenIsRejected() {
        AuthService service = new AuthService(
                WebClient.builder().exchangeFunction(request -> Mono.just(
                        ClientResponse.create(HttpStatus.BAD_REQUEST)
                                .header("Content-Type", "application/json")
                                .body("{\"error\":\"invalid_grant\"}")
                                .build())),
                new ObjectMapper(),
                "http://keycloak", "banking", "banking-service-api", "client-secret");
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("expired");

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> service.refresh(request));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void rejectsBlankRefreshTokenBeforeCallingKeycloak() {
        AuthService service = new AuthService(
                WebClient.builder().exchangeFunction(request -> {
                    throw new AssertionError("Keycloak must not be called");
                }),
                new ObjectMapper(),
                "http://keycloak", "banking", "banking-service-api", "client-secret");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.refresh(new RefreshTokenRequest()));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    private AuthService logoutService(HttpStatus keycloakStatus, String[] capturedPath) {
        return new AuthService(
                WebClient.builder().exchangeFunction(request -> {
                    capturedPath[0] = request.url().getPath();
                    return Mono.just(ClientResponse.create(keycloakStatus)
                            .header("Content-Type", "application/json")
                            .body(keycloakStatus == HttpStatus.BAD_REQUEST ? "{\"error\":\"invalid_grant\"}" : "")
                            .build());
                }),
                new ObjectMapper(),
                "http://keycloak", "banking", "banking-service-api", "client-secret");
    }

    @Test
    void logoutRevokesRefreshTokenAtKeycloak() {
        String[] path = new String[1];
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh-value");

        logoutService(HttpStatus.NO_CONTENT, path).logout(request);

        assertEquals("/realms/banking/protocol/openid-connect/logout", path[0]);
    }

    @Test
    void logoutIsIdempotentWhenSessionAlreadyEnded() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("expired");

        logoutService(HttpStatus.BAD_REQUEST, new String[1]).logout(request);
    }

    @Test
    void logoutMapsKeycloakServerErrorsToBadGateway() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh-value");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> logoutService(HttpStatus.SERVICE_UNAVAILABLE, new String[1]).logout(request));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
    }

    @Test
    void logoutRejectsBlankRefreshToken() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> logoutService(HttpStatus.NO_CONTENT, new String[1]).logout(new RefreshTokenRequest()));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
