package com.banking.api.controller;

import com.banking.api.dto.ServiceRoleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Publishes the catalogue of requestable services. The same rules decide which roles are
 * requestable through a role application, so a service listed here can be requested and a
 * service absent here cannot.
 *
 * <p>Public: the sign-up and services pages render the catalogue before anyone signs in, and
 * the identical catalogue is already readable in the public Swagger docs.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ServiceCatalogController {

    /** Roles that come with the account or the realm and are never requestable services. */
    private static final Set<String> SYSTEM_ROLES = Set.of(
            "ROLE_ADMIN", "ROLE_CLIENT", "default-roles-banking", "uma_authorization", "offline_access"
    );

    private final Keycloak keycloakAdmin;

    @Value("${keycloak.realm}")
    private String realm;

    @Operation(
            summary = "List platform services",
            description = "Every service a user can be granted, read live from Keycloak. System roles "
                    + "(`ROLE_ADMIN`, `ROLE_CLIENT` and the realm defaults) are never included. "
                    + "Public - no token required.",
            tags = "Services"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "All requestable services, sorted by role name",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ServiceRoleResponse.class)))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Keycloak could not be reached",
                    content = @Content(schema = @Schema(implementation = com.banking.api.exception.ErrorResponse.class))
            )
    })
    @GetMapping("/api/services")
    public ResponseEntity<List<ServiceRoleResponse>> getServices() {
        List<ServiceRoleResponse> services = keycloakAdmin.realm(realm).roles().list().stream()
                .filter(role -> !SYSTEM_ROLES.contains(role.getName()))
                .sorted(Comparator.comparing(RoleRepresentation::getName))
                .map(role -> new ServiceRoleResponse(
                        role.getName(),
                        role.getDescription() == null ? "" : role.getDescription()
                ))
                .toList();
        log.debug("Service catalogue contains {} services", services.size());
        return ResponseEntity.ok(services);
    }
}
