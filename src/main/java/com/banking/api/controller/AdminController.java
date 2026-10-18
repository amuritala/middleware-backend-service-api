package com.banking.api.controller;

import com.banking.api.dto.ApproveClientRequest;
import com.banking.api.dto.ClientResponse;
import com.banking.api.dto.RegisterRequest;
import com.banking.api.dto.AdminUserResponse;
import com.banking.api.dto.UpdateUserRequest;
import com.banking.api.dto.UserRolesRequest;
import com.banking.api.entity.ClientStatus;
import com.banking.api.exception.ErrorResponse;
import com.banking.api.service.AdminService;
import com.banking.api.dto.DownstreamAuditResponse;
import com.banking.api.service.DownstreamAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springdoc.core.annotations.ParameterObject;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@RestController
@RequestMapping(value = "/api/admin", produces = "application/json")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@SecurityRequirement(name = "keycloak")
public class AdminController {

    private final AdminService adminService;
    private final DownstreamAuditService downstreamAuditService;

    @Operation(
            summary = "Create a vendor account directly",
            description = "Creates the Keycloak user with the supplied temporary password and activates the client "
                    + "immediately - admin-created vendors never pass through the PENDING review queue. The local "
                    + "user is flagged so the vendor must change the temporary password at first sign-in. "
                    + "ROLE_CLIENT is assigned automatically; grant service roles afterwards through the user "
                    + "management endpoints.",
            tags = "Admin - Clients"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Client created and already active",
                    content = @Content(schema = @Schema(implementation = ClientResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed - `details` maps each field name to its message",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "A user with this email address already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/clients")
    public ResponseEntity<ClientResponse> createClient(@Valid @RequestBody RegisterRequest request) {
        log.info("Admin creating client: {}", request.getCompanyName());
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createClient(request));
    }

    @Operation(
            summary = "List all clients",
            description = "Every client (vendor) on the platform, whatever its status.",
            tags = "Admin - Clients"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "All registered clients",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ClientResponse.class)))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/clients")
    public ResponseEntity<List<ClientResponse>> getAllClients(Authentication authentication) {
        // Extract user info from JWT if needed
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaim("sub");
        String email = jwt.getClaim("email");

        log.info("Admin {} accessing all clients", email);
        return ResponseEntity.ok(adminService.getAllClients());
    }

    @Operation(
            summary = "List assignable service roles",
            description = "Realm roles from Keycloak that an administrator may grant to a client. ROLE_ADMIN, "
                    + "ROLE_CLIENT and Keycloak's own housekeeping roles are excluded.",
            tags = "Admin - Roles"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Role names, e.g. ROLE_VIEWER",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = String.class)))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/roles")
    public ResponseEntity<List<String>> getKeycloakRoles() {
        return ResponseEntity.ok(adminService.getKeycloakRoles());
    }

    @Operation(
            summary = "List clients awaiting approval",
            description = "Clients in PENDING status - the queue an administrator reviews.",
            tags = "Admin - Clients"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Pending clients",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ClientResponse.class)))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/clients/pending")
    public ResponseEntity<List<ClientResponse>> getPendingClients() {
        return ResponseEntity.ok(adminService.getClientsByStatus(ClientStatus.PENDING));
    }

    @Operation(
            summary = "Get one client",
            description = "Full detail for a single client, including its status and user count.",
            tags = "Admin - Clients"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "The requested client",
                    content = @Content(schema = @Schema(implementation = ClientResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No client exists with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/clients/{clientId}")
    public ResponseEntity<ClientResponse> getClient(
            @Parameter(description = "Client identifier") @PathVariable UUID clientId
    ) {
        return ResponseEntity.ok(adminService.getClientById(clientId));
    }


    @Operation(
            summary = "Approve a pending client",
            description = "Moves the client from PENDING to APPROVED, enables its Keycloak user and grants the supplied "
                    + "service roles. ROLE_CLIENT is always added automatically.",
            tags = "Admin - Clients"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Client approved",
                    content = @Content(schema = @Schema(implementation = ClientResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No client exists with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The client is no longer in PENDING state",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PutMapping("/clients/{clientId}/approve")
    public ResponseEntity<ClientResponse> approveClient(
            @Parameter(description = "Client identifier") @PathVariable UUID clientId,
            @RequestBody ApproveClientRequest request
    ) {
        log.info(
                "Approving client: {} with roles: {}",
                clientId,
                request.approvedRoles()
        );

        return ResponseEntity.ok(
                adminService.approveClient(
                        clientId,
                        request.approvedRoles()
                )
        );
    }

    @Operation(
            summary = "Reject a pending client",
            description = "Moves the client from PENDING to REJECTED and disables its Keycloak user.",
            tags = "Admin - Clients"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Client rejected",
                    content = @Content(schema = @Schema(implementation = ClientResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No client exists with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The client is no longer in PENDING state",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PutMapping("/clients/{clientId}/reject")
    public ResponseEntity<ClientResponse> rejectClient(
            @Parameter(description = "Client identifier") @PathVariable UUID clientId
    ) {
        log.info("Rejecting client: {}", clientId);
        return ResponseEntity.ok(adminService.rejectClient(clientId));
    }

    @Operation(
            summary = "Suspend a client",
            description = "Suspends the client, revokes all of its API keys and disables its Keycloak user.",
            tags = "Admin - Clients"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Client suspended",
                    content = @Content(schema = @Schema(implementation = ClientResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No client exists with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PutMapping("/clients/{clientId}/suspend")
    public ResponseEntity<ClientResponse> suspendClient(
            @Parameter(description = "Client identifier") @PathVariable UUID clientId
    ) {
        log.info("Suspending client: {}", clientId);
        return ResponseEntity.ok(adminService.suspendClient(clientId));
    }

    @Operation(
            summary = "Activate a client",
            description = "Re-activates a client, re-enables its Keycloak user and (re)grants the supplied service roles. "
                    + "ROLE_CLIENT is always added automatically.",
            tags = "Admin - Clients"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Client activated",
                    content = @Content(schema = @Schema(implementation = ClientResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No client exists with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PutMapping("/clients/{clientId}/activate")
    public ResponseEntity<ClientResponse> activateClient(
            @Parameter(description = "Client identifier") @PathVariable UUID clientId,
            @RequestBody ApproveClientRequest request) {
        log.info("Activating client: {}", clientId);
        return ResponseEntity.ok(adminService.activateClient(clientId, request.approvedRoles()));
    }

    @Operation(
            summary = "Page through downstream audit records",
            description = "Log of the calls this service made to downstream systems on behalf of users. The body is a "
                    + "Spring `Page` envelope: `content` holds the records, alongside `totalElements`, `totalPages`, "
                    + "`number` and `size`. Sort with e.g. `?page=0&size=20&sort=startTime,desc`.",
            tags = "Admin - Audit"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page of audit records"),
            @ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/downstream-audit")
    public ResponseEntity<Page<DownstreamAuditResponse>> getDownstreamAudit(
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(downstreamAuditService.findAll(pageable));
    }

    @Operation(
            summary = "Search Keycloak users",
            description = "Lists users from the realm. Pass `search` to match on username, email, first or last name.",
            tags = "Admin - Users"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Matching users, each with its realm roles",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AdminUserResponse.class)))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/users")
    public ResponseEntity<List<AdminUserResponse>> getUsers(
            @Parameter(description = "Free-text search term. Omit to list every user.")
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(adminService.getKeycloakUsers(search));
    }

    @Operation(
            summary = "Get one user",
            description = "Single Keycloak user, including the realm roles currently assigned to it.",
            tags = "Admin - Users"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "The requested user",
                    content = @Content(schema = @Schema(implementation = AdminUserResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No Keycloak user exists with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/users/{userId}")
    public ResponseEntity<AdminUserResponse> getUser(
            @Parameter(description = "Keycloak user id (the JWT `sub` claim)") @PathVariable String userId
    ) {
        return ResponseEntity.ok(adminService.getKeycloakUser(userId));
    }

    @Operation(
            summary = "Update a user's profile",
            description = "Updates the mutable Keycloak attributes. Send only the fields you want to change; omitted "
                    + "fields are left untouched.",
            tags = "Admin - Users"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "The updated user",
                    content = @Content(schema = @Schema(implementation = AdminUserResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No Keycloak user exists with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PutMapping("/users/{userId}")
    public ResponseEntity<AdminUserResponse> updateUser(
            @Parameter(description = "Keycloak user id (the JWT `sub` claim)") @PathVariable String userId,
            @RequestBody UpdateUserRequest request
    ) {
        return ResponseEntity.ok(adminService.updateKeycloakUser(userId, request));
    }

    @Operation(
            summary = "Assign realm roles to a user",
            description = "Adds service roles to the user in Keycloak. Protected roles (ROLE_ADMIN, ROLE_CLIENT and "
                    + "Keycloak's own housekeeping roles) are refused, as are unknown role names.",
            tags = "Admin - Roles"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "The user with its updated role list",
                    content = @Content(schema = @Schema(implementation = AdminUserResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "`roles` is missing or empty, a protected role was requested, or a role name is unknown",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No Keycloak user exists with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PutMapping("/users/{userId}/roles")
    public ResponseEntity<AdminUserResponse> assignRoles(
            @Parameter(description = "Keycloak user id (the JWT `sub` claim)") @PathVariable String userId,
            @Valid @RequestBody UserRolesRequest request
    ) {
        return ResponseEntity.ok(adminService.assignRoles(userId, request));
    }

    @Operation(
            summary = "Remove realm roles from a user",
            description = "Revokes service roles from the user in Keycloak. Takes the roles to remove in the request body, "
                    + "the same shape as the assign call.",
            tags = "Admin - Roles"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "The user with its updated role list",
                    content = @Content(schema = @Schema(implementation = AdminUserResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "`roles` is missing or empty, a protected role was requested, or a role name is unknown",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No Keycloak user exists with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @DeleteMapping("/users/{userId}/roles")
    public ResponseEntity<AdminUserResponse> unassignRoles(
            @Parameter(description = "Keycloak user id (the JWT `sub` claim)") @PathVariable String userId,
            @Valid @RequestBody UserRolesRequest request
    ) {
        return ResponseEntity.ok(adminService.unassignRoles(userId, request));
    }

}