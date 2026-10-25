package com.banking.api.service;

import com.banking.api.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

/**
 * The portal's sign-in, refresh and sign-out grants, resolved against Keycloak.
 *
 * <p>The frontend only ever speaks this API's {@code /api/auth/**} contract; this service is the
 * single place that turns that contract into Keycloak token-endpoint calls, using the confidential
 * {@code banking-onboarding-api} client already configured in {@code application.yml}. Passwords are
 * forwarded to Keycloak and are never persisted or logged here.</p>
 *
 * <p>Status and body are always classified in one place, from either the normal or the error path,
 * so that "wrong password" (the user's problem) stays distinguishable from "our client credentials
 * were rejected" (ours) no matter how the HTTP client surfaces an error status.</p>
 */
@Service
@Slf4j
public class AuthService {

    private final String authServerUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;
    private final RestTemplate tokenClient;

    @Autowired
    public AuthService(
            @Value("${keycloak.auth-server-url}") String authServerUrl,
            @Value("${keycloak.realm}") String realm,
            @Value("${keycloak.resource}") String clientId,
            @Value("${keycloak.credentials.secret}") String clientSecret
    ) {
        this(authServerUrl, realm, clientId, clientSecret, lenientClient());
    }

    /** Test seam: the callers above supply a client that never throws on an error status. */
    AuthService(
            String authServerUrl,
            String realm,
            String clientId,
            String clientSecret,
            RestTemplate tokenClient
    ) {
        this.authServerUrl = authServerUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenClient = tokenClient;
    }

    /** A 4xx/5xx from Keycloak carries the reason we must report, so it is not an HTTP error here. */
    private static RestTemplate lenientClient() {
        RestTemplate client = new RestTemplate();
        client.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        return client;
    }

    /** Username/password -> the access + refresh token payload (Keycloak direct grant). */
    public String login(String username, String password) {
        MultiValueMap<String, String> form = grant("password");
        form.add("username", username);
        form.add("password", password);
        return exchange(form, "password", "Invalid username or password");
    }

    /** Refresh token -> a rotated access + refresh token payload. */
    public String refresh(String refreshToken) {
        MultiValueMap<String, String> form = grant("refresh_token");
        form.add("refresh_token", refreshToken);
        return exchange(form, "refresh_token", "Your session has expired. Please sign in again.");
    }

    /**
     * Revokes the refresh token so the Keycloak session ends server-side. Best effort: the caller
     * clears its local tokens either way, and a token that is never revoked still expires on its
     * own - a Keycloak hiccup must not turn sign-out into an error the user has to deal with.
     */
    public void revoke(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);
        try {
            tokenClient.postForEntity(endpoint("logout"), entity(form), String.class);
        } catch (RuntimeException ex) {
            log.warn("Could not revoke the Keycloak session: {}", ex.getMessage());
        }
    }

    private MultiValueMap<String, String> grant(String grantType) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", grantType);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        return form;
    }

    private HttpEntity<MultiValueMap<String, String>> entity(MultiValueMap<String, String> form) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return new HttpEntity<>(form, headers);
    }

    private String endpoint(String path) {
        return authServerUrl + "/realms/" + realm + "/protocol/openid-connect/" + path;
    }

    /**
     * Turns Keycloak's reason into something the sign-in screen can actually show. A wrong password
     * keeps the generic message, but an account Keycloak refuses outright (a self-registered user
     * whose email is still unverified answers "Account is not fully set up") must say so - otherwise
     * the portal tells the user their password is wrong and they keep retyping one that is fine.
     */
    private static String describeRejection(String body, String fallback) {
        String description = field(body, "error_description");
        if (description == null || description.contains("Invalid user credentials")) {
            return fallback;
        }
        if (description.contains("not fully set up")) {
            return "This account is not fully set up yet. Verify your email address, then sign in again.";
        }
        if (description.contains("disabled")) {
            return "This account has been disabled. Please contact your administrator.";
        }
        return fallback;
    }

    /** Reads a JSON string field out of an error body without pulling in a JSON parser. */
    private static String field(String body, String name) {
        int at = body.indexOf("\"" + name + "\"");
        if (at < 0) {
            return null;
        }
        int colon = body.indexOf(':', at + name.length() + 2);
        int quote = colon < 0 ? -1 : body.indexOf('"', colon);
        if (quote < 0) {
            return null;
        }
        int end = body.indexOf('"', quote + 1);
        return end < 0 ? null : body.substring(quote + 1, end);
    }

    private String exchange(
            MultiValueMap<String, String> form, String grant, String rejectedMessage
    ) {
        int status;
        String body;
        try {
            ResponseEntity<String> response =
                    tokenClient.postForEntity(endpoint("token"), entity(form), String.class);
            status = response.getStatusCode().value();
            body = response.getBody() == null ? "" : response.getBody();
        } catch (RestClientResponseException ex) {
            status = ex.getStatusCode().value();
            body = ex.getResponseBodyAsString() == null ? "" : ex.getResponseBodyAsString();
        } catch (ResourceAccessException ex) {
            log.error("Keycloak is unreachable during the {} grant: {}", grant, ex.getMessage());
            throw new ApiException(
                    "Authentication Unavailable",
                    "The authentication service is unreachable",
                    HttpStatus.SERVICE_UNAVAILABLE);
        } catch (RestClientException ex) {
            log.error("Could not call Keycloak for the {} grant: {}", grant, ex.getMessage());
            throw new ApiException(
                    "Authentication Unavailable",
                    "The authentication service rejected the request",
                    HttpStatus.BAD_GATEWAY);
        }

        if (status >= 200 && status < 300) {
            if (!body.contains("access_token")) {
                log.error("Keycloak answered the {} grant with {} but no access token", grant, status);
                throw new ApiException(
                        "Authentication Unavailable",
                        "The authentication service returned an invalid access token",
                        HttpStatus.BAD_GATEWAY);
            }
            return body;
        }

        // A wrong password or a dead refresh token comes back as 400/401 invalid_grant: the
        // caller's credentials or session are simply no good, which the sign-in screen can state.
        // Keycloak sometimes answers that 401 without a body, so the status alone is enough here -
        // and a rejected client secret is the one case we must not swallow, see below.
        if ((status == HttpStatus.BAD_REQUEST.value() || status == HttpStatus.UNAUTHORIZED.value())
                && !body.contains("unauthorized_client")) {
            log.info("Keycloak rejected the {} grant with {}: {}", grant, status, body);
            throw new ApiException(
                    "Invalid Credentials",
                    describeRejection(body, rejectedMessage),
                    HttpStatus.UNAUTHORIZED);
        }

        // Anything else - a rotated client secret, a misconfigured realm - is our problem, and must
        // not masquerade as "wrong password" on the sign-in screen.
        log.error("Keycloak rejected the {} grant: {} {}", grant, status, body);
        throw new ApiException(
                "Authentication Unavailable",
                "The authentication service rejected the request",
                HttpStatus.BAD_GATEWAY);
    }
}
