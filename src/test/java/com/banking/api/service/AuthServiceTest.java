package com.banking.api.service;

import com.banking.api.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The classification behind the sign-in screen: a wrong password must read as one, an account
 * Keycloak refuses outright must say why, and a broken client configuration must never be blamed
 * on the person typing their password.
 */
class AuthServiceTest {

    private static final String KEYCLOAK = "http://keycloak:8080";
    private static final String REALM = "banking";
    private static final String CLIENT = "banking-onboarding-api";
    private static final String SECRET = "test-secret";
    private static final String TOKEN_URL =
            KEYCLOAK + "/realms/" + REALM + "/protocol/openid-connect/token";

    private static final String TOKEN_PAYLOAD =
            "{\"access_token\":\"header.payload.signature\",\"refresh_token\":\"refresh\",\"expires_in\":300}";

    private RestTemplate tokenClient;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        tokenClient = mock(RestTemplate.class);
        authService = new AuthService(KEYCLOAK, REALM, CLIENT, SECRET, tokenClient);
    }

    @Test
    void loginForwardsThePasswordGrantAndReturnsTheTokenPayload() {
        keycloakRespondsWith(HttpStatus.OK, TOKEN_PAYLOAD);

        String payload = authService.login("one@skybank.sl", "S3cret!pass");

        assertEquals(TOKEN_PAYLOAD, payload);

        ArgumentCaptor<HttpEntity> sent = ArgumentCaptor.forClass(HttpEntity.class);
        verify(tokenClient).postForEntity(eq(TOKEN_URL), sent.capture(), eq(String.class));

        @SuppressWarnings("unchecked")
        MultiValueMap<String, String> form =
                (MultiValueMap<String, String>) sent.getValue().getBody();
        assertEquals("password", form.getFirst("grant_type"));
        assertEquals("one@skybank.sl", form.getFirst("username"));
        assertEquals("S3cret!pass", form.getFirst("password"));
        assertEquals(CLIENT, form.getFirst("client_id"));
        assertEquals(SECRET, form.getFirst("client_secret"));
    }

    @Test
    void aWrongPasswordIsReportedAsInvalidCredentials() {
        keycloakRespondsWith(
                HttpStatus.UNAUTHORIZED,
                "{\"error\":\"invalid_grant\",\"error_description\":\"Invalid user credentials\"}");

        ApiException error = assertThrows(ApiException.class,
                () -> authService.login("one@skybank.sl", "wrong"));

        assertEquals(401, error.getStatus().value());
        assertEquals("Invalid username or password", error.getErrorMessage());
    }

    @Test
    void anAccountStillAwaitingEmailVerificationIsToldWhy() {
        keycloakRespondsWith(
                HttpStatus.BAD_REQUEST,
                "{\"error\":\"invalid_grant\",\"error_description\":\"Account is not fully set up\"}");

        ApiException error = assertThrows(ApiException.class,
                () -> authService.login("new@skybank.sl", "S3cret!pass"));

        assertEquals(401, error.getStatus().value());
        assertTrue(error.getErrorMessage().contains("not fully set up"),
                "expected the real reason, got: " + error.getErrorMessage());
    }

    @Test
    void aRejectedClientSecretIsNotBlamedOnThePersonSigningIn() {
        keycloakRespondsWith(
                HttpStatus.UNAUTHORIZED,
                "{\"error\":\"unauthorized_client\",\"error_description\":\"Invalid client credentials\"}");

        ApiException error = assertThrows(ApiException.class,
                () -> authService.login("one@skybank.sl", "S3cret!pass"));

        assertEquals(502, error.getStatus().value());
        assertEquals("The authentication service rejected the request", error.getErrorMessage());
    }

    @Test
    void anExpiredRefreshTokenEndsTheSessionWithASessionMessage() {
        keycloakRespondsWith(
                HttpStatus.BAD_REQUEST,
                "{\"error\":\"invalid_grant\",\"error_description\":\"Invalid refresh token\"}");

        ApiException error = assertThrows(ApiException.class,
                () -> authService.refresh("stale-token"));

        assertEquals(401, error.getStatus().value());
        assertTrue(error.getErrorMessage().contains("session has expired"),
                "expected a session message, got: " + error.getErrorMessage());
    }

    @Test
    void anUnreachableKeycloakIsReportedAsAnOutageRatherThanBadCredentials() {
        when(tokenClient.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        ApiException error = assertThrows(ApiException.class,
                () -> authService.login("one@skybank.sl", "S3cret!pass"));

        assertEquals(503, error.getStatus().value());
        assertEquals("The authentication service is unreachable", error.getErrorMessage());
    }

    @Test
    void anyOtherTransportFailureIsReportedAsAGatewayProblem() {
        when(tokenClient.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RestClientException("unexpected response"));

        ApiException error = assertThrows(ApiException.class,
                () -> authService.login("one@skybank.sl", "S3cret!pass"));

        assertEquals(502, error.getStatus().value());
    }

    @Test
    void aResponseWithoutATokenIsNeverTreatedAsSuccess() {
        keycloakRespondsWith(HttpStatus.OK, "{\"note\":\"no token here\"}");

        ApiException error = assertThrows(ApiException.class,
                () -> authService.login("one@skybank.sl", "S3cret!pass"));

        assertEquals(502, error.getStatus().value());
    }

    @Test
    void signOutSwallowsFailuresSoTheLocalSessionStillEnds() {
        when(tokenClient.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertDoesNotThrow(() -> authService.revoke("refresh-token"));
    }

    private void keycloakRespondsWith(HttpStatus status, String body) {
        when(tokenClient.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(body, status));
    }
}
