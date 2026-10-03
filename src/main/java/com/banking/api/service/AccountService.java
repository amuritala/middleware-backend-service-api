package com.banking.api.service;

import com.banking.api.dto.ChangePasswordRequest;
import com.banking.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Self-service account operations for the signed-in user - currently the password change.
 * Verifying the current password uses Keycloak's Direct Access Grant against the confidential
 * `banking-onboarding-api` client, so the realm's own password policy is what actually decides.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private static final String GRANT_TYPE = "password";
    private static final String GRANT_TYPE_FIELD = "grant_type";
    private static final String CLIENT_ID_FIELD = "client_id";
    private static final String CLIENT_SECRET_FIELD = "client_secret";
    private static final String USERNAME_FIELD = "username";
    private static final String PASSWORD_FIELD = "password";

    private final Keycloak keycloakAdmin;
    private final RestTemplate restTemplate;

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.resource}")
    private String clientId;

    @Value("${keycloak.credentials.secret}")
    private String clientSecret;

    /**
     * Change the calling user's password. The current password is re-verified against Keycloak
     * with a Direct Access Grant (wrong password => 401), then the new credential is written
     * through the admin API. Only the caller's own account can ever be touched: the user id
     * comes from the JWT, never from the request.
     */
    public ResponseEntity<Map<String, String>> changePassword(Jwt jwt, ChangePasswordRequest request) {
        String userId = jwt.getSubject();
        String username = jwt.getClaim("preferred_username");

        verifyCurrentPassword(username, request.currentPassword());
        resetPassword(userId, request.newPassword());

        log.info("User {} changed their password", username);
        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }

    private void verifyCurrentPassword(String username, String currentPassword) {
        // Keycloak's token endpoint only accepts application/x-www-form-urlencoded - a plain
        // Map would be serialised as JSON and every verification would fail.
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add(CLIENT_ID_FIELD, clientId);
        form.add(CLIENT_SECRET_FIELD, clientSecret);
        form.add(GRANT_TYPE_FIELD, GRANT_TYPE);
        form.add(USERNAME_FIELD, username);
        form.add(PASSWORD_FIELD, currentPassword);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    tokenEndpoint(), form, Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw wrongPassword();
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // Keycloak quirk: BOTH a wrong password and a bad client secret can arrive as HTTP
            // 401, so discriminate by the OAuth error code in the body, not the status:
            //   invalid_grant      -> the user's password was wrong (expected path)
            //   unauthorized_client / invalid_client -> this service's client config is broken
            String body = e.getResponseBodyAsString();
            if (body.contains("unauthorized_client") || body.contains("invalid_client")) {
                log.error(
                        "Password verification failed: Keycloak rejected the client credentials. "
                                + "Check keycloak.credentials.secret against the realm's client secret.");
                throw new ApiException(
                        "VERIFICATION_UNAVAILABLE",
                        "Password verification is temporarily unavailable. Please try again shortly.",
                        HttpStatus.SERVICE_UNAVAILABLE
                );
            }
            throw wrongPassword();
        }
    }

    private ApiException wrongPassword() {
        return new ApiException(
                "INVALID_CREDENTIALS",
                "The current password you entered is incorrect",
                HttpStatus.UNAUTHORIZED
        );
    }

    private void resetPassword(String userId, String newPassword) {
        RealmResource realmResource = keycloakAdmin.realm(realm);

        UserRepresentation user = realmResource.users().get(userId).toRepresentation();
        if (user == null) {
            throw new ApiException(
                    "USER_NOT_FOUND",
                    "No account exists for this session",
                    HttpStatus.NOT_FOUND
            );
        }

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(newPassword);
        credential.setTemporary(false);
        realmResource.users().get(userId).resetPassword(credential);
    }

    private String tokenEndpoint() {
        return authServerUrl.replaceAll("/+$", "")
                + "/realms/" + realm + "/protocol/openid-connect/token";
    }
}
