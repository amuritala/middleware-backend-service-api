package com.banking.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI(
            @Value("${keycloak.auth-server-url}") String keycloakUrl,
            @Value("${keycloak.realm}") String realm
    ) {
        String baseUrl = keycloakUrl.replaceAll("/+$", "");
        String realmUrl = baseUrl + "/realms/" + realm;

        return new OpenAPI()
                .info(new Info()
                        .title("Banking Onboarding API")
                        .version("1.0.0")
                        .description("Vendor onboarding and service-role management for the banking platform.\n\n"
                                + "**Authentication** - every endpoint except `POST /api/register`, "
                                + "`GET /api/check-availability` and `GET /api/services` requires a Keycloak access token. Click "
                                + "**Authorize**, sign in to the `banking` realm, and that token is then reused for "
                                + "every \"Try it out\" call.\n\n"
                                + "**Roles** - administrators (`ROLE_ADMIN`) review onboarding applications, manage "
                                + "users and action role requests. Vendors (`ROLE_CLIENT`) apply for service roles "
                                + "on their own account. Endpoints note the authority they require.\n\n"
                                + "**Errors** - failures return an `ErrorResponse`. Validation failures come back as "
                                + "`400` with a `details` map of field name to message."))
                .tags(List.of(
                        new Tag().name("Registration")
                                .description("Public sign-up endpoints. No token required."),
                        new Tag().name("Services")
                                .description("The catalogue of services a user can hold or request. Public, no token required."),
                        new Tag().name("Admin - Clients")
                                .description("Review and manage client (vendor) onboarding. Requires ROLE_ADMIN."),
                        new Tag().name("Admin - Users")
                                .description("Read and update Keycloak users. Requires ROLE_ADMIN."),
                        new Tag().name("Admin - Roles")
                                .description("List assignable roles and grant or revoke them. Requires ROLE_ADMIN."),
                        new Tag().name("Admin - Audit")
                                .description("Inspect downstream calls made on behalf of users. Requires ROLE_ADMIN."),
                        new Tag().name("Customer - Role Applications")
                                .description("Vendors request service roles; administrators review the queue."),
                        new Tag().name("Account")
                                .description("Self-service operations on your own account - e.g. changing your password. "
                                        + "Available to every signed-in user.")))
                .components(new Components()
                        .addSecuritySchemes("keycloak", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .flows(new OAuthFlows()
                                        .authorizationCode(new OAuthFlow()
                                                .authorizationUrl(realmUrl + "/protocol/openid-connect/auth")
                                                .tokenUrl(realmUrl + "/protocol/openid-connect/token")
                                                .scopes(new Scopes()
                                                        .addString("openid", "OpenID")
                                                        .addString("profile", "Profile"))))));
    }
}
