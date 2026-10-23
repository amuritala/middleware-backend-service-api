package com.banking.api.service;

import com.banking.api.dto.AuthRequest;
import com.banking.api.dto.AuthResponse;
import com.banking.api.dto.RefreshTokenRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class AuthService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    @Autowired
    public AuthService(
            WebClient.Builder webClientBuilder,
            @Value("${keycloak.auth-server-url}") String authServerUrl,
            @Value("${keycloak.realm}") String realm,
            @Value("${keycloak.resource}") String clientId,
            @Value("${keycloak.credentials.secret:}") String clientSecret) {
        this(webClientBuilder, new ObjectMapper(), authServerUrl, realm, clientId, clientSecret);
    }

    AuthService(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            String authServerUrl,
            String realm,
            String clientId,
            String clientSecret) {
        this.webClient = webClientBuilder.baseUrl(authServerUrl).build();
        this.objectMapper = objectMapper;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret == null ? "" : clientSecret;
    }

    public AuthResponse login(AuthRequest request) {
        if (request == null || request.getUsername() == null || request.getUsername().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username and password are required");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("username", request.getUsername());
        form.add("password", request.getPassword());

        return requestTokens(form, "Invalid username or password");
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refresh token is required");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", request.getRefreshToken());

        return requestTokens(form, "Refresh token is invalid or expired");
    }

    public void logout(RefreshTokenRequest request) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refresh token is required");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        if (!clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        form.add("refresh_token", request.getRefreshToken());

        webClient.post()
                .uri("/realms/{realm}/protocol/openid-connect/logout", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .exchangeToMono(response -> {
                    HttpStatusCode status = response.statusCode();
                    // Keycloak answers 400 invalid_grant for an already-ended session; logout is idempotent.
                    if (status.is2xxSuccessful() || status.value() == HttpStatus.BAD_REQUEST.value()) {
                        return response.releaseBody();
                    }
                    log.error("Keycloak logout endpoint returned {} for realm={} clientId={}",
                            status, realm, clientId);
                    return response.releaseBody().then(Mono.error(new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY, "Keycloak authentication service is unavailable")));
                })
                .onErrorMap(WebClientRequestException.class, exception -> {
                    log.error("Unable to reach the Keycloak logout endpoint", exception);
                    return new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY, "Unable to reach Keycloak authentication service", exception);
                })
                .block();
    }

    private AuthResponse requestTokens(MultiValueMap<String, String> form, String invalidGrantMessage) {
        log.debug("Requesting Keycloak token: grant={}, realm={}, clientId={}, clientSecretConfigured={}",
                form.getFirst("grant_type"), realm, clientId, !clientSecret.isBlank());

        form.add("client_id", clientId);
        if (!clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }

        KeycloakTokenResponse tokenResponse = webClient.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> handleClientError(response, invalidGrantMessage))
                .onStatus(HttpStatusCode::is5xxServerError, response -> {
                    log.error("Keycloak token endpoint returned {} for realm={} clientId={}",
                            response.statusCode(), realm, clientId);
                    return response.releaseBody().thenReturn(new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY, "Keycloak authentication service is unavailable"));
                })
                .bodyToMono(KeycloakTokenResponse.class)
                .onErrorMap(WebClientRequestException.class, exception -> {
                    log.error("Unable to reach the Keycloak token endpoint", exception);
                    return new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY, "Unable to reach Keycloak authentication service", exception);
                })
                .onErrorMap(DecodingException.class, exception -> {
                    log.error("Keycloak returned an invalid token response", exception);
                    return new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY, "Keycloak returned an invalid token response", exception);
                })
                .block();

        if (tokenResponse == null || tokenResponse.getAccessToken() == null
                || tokenResponse.getAccessToken().isBlank()
                || tokenResponse.getRefreshToken() == null
                || tokenResponse.getRefreshToken().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Keycloak returned an incomplete token response");
        }

        return new AuthResponse(tokenResponse.getAccessToken(), tokenResponse.getRefreshToken());
    }

    private Mono<? extends Throwable> handleClientError(ClientResponse response, String invalidGrantMessage) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> {
                    KeycloakError keycloakError = readKeycloakError(body);
                    String errorCode = keycloakError.code();
                    String errorDescription = sanitizeLogValue(keycloakError.description());
                    if ("invalid_grant".equals(errorCode)) {
                        log.warn("Keycloak rejected grant: status={}, error={}, "
                                        + "description={}, realm={}, clientId={}",
                                response.statusCode(), errorCode, errorDescription, realm, clientId);
                        return new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED, invalidGrantMessage);
                    }

                    log.error("Keycloak rejected token request: status={}, error={}, "
                                    + "description={}, realm={}, clientId={}, clientSecretConfigured={}. "
                                    + "Check client enabled status, Direct access grants, client authentication, "
                                    + "and the runtime client secret.",
                            response.statusCode(),
                            errorCode == null ? "unknown" : errorCode,
                            errorDescription,
                            realm,
                            clientId,
                            !clientSecret.isBlank());
                    return new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            "Keycloak rejected the token request; check client configuration and direct access grants");
                });
    }

    private KeycloakError readKeycloakError(String responseBody) {
        if (responseBody.isBlank()) {
            return new KeycloakError(null, null);
        }
        try {
            JsonNode errorResponse = objectMapper.readTree(responseBody);
            JsonNode error = errorResponse.get("error");
            JsonNode description = errorResponse.get("error_description");
            return new KeycloakError(
                    error != null && error.isTextual() ? error.asText() : null,
                    description != null && description.isTextual() ? description.asText() : null);
        } catch (JsonProcessingException exception) {
            log.debug("Keycloak returned a non-JSON error response for realm={} clientId={}", realm, clientId);
            return new KeycloakError(null, null);
        }
    }

    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "not provided";
        }
        String singleLine = value.replaceAll("[\\r\\n\\t\\p{Cntrl}]", " ").trim();
        return singleLine.length() > 512 ? singleLine.substring(0, 512) : singleLine;
    }

    private record KeycloakError(String code, String description) {
    }

    @Data
    private static class KeycloakTokenResponse {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("refresh_token")
        private String refreshToken;
    }
}
