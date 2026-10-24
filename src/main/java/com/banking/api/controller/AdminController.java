package com.banking.api.controller;

import com.banking.api.dto.ApproveClientRequest;
import com.banking.api.dto.ApprovalRequestResponse;
import com.banking.api.dto.ClientResponse;
import com.banking.api.dto.InitiateAdminUserRequest;
import com.banking.api.dto.InitiateClientActionRequest;
import com.banking.api.dto.InitiateServiceAssignmentRequest;
import com.banking.api.dto.InitiateVendorRegistrationRequest;
import com.banking.api.dto.AuditLogResponse;
import com.banking.api.dto.AdminUserResponse;
import com.banking.api.dto.UpdateUserRequest;
import com.banking.api.dto.UserRolesRequest;
import com.banking.api.entity.ApprovalActionType;
import com.banking.api.entity.ClientStatus;
import com.banking.api.exception.ErrorResponse;
import com.banking.api.service.AdminService;
import com.banking.api.service.ApprovalService;
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
    private final ApprovalService approvalService;
    private final DownstreamAuditService downstreamAuditService;

    @Operation(
            summary = "Initiate a vendor registration (maker-checker step 1)",
            description = "Stages a vendor: the Keycloak account is created disabled and the local client sits "
                    + "in PENDING until an authorizer approves the request. On approval the vendor becomes "
                    + "ACTIVE and a generated temporary password is emailed to the contact - no credential is "
                    + "ever returned by the API. Requires the INITIATOR or SUPER_ADMIN governance role.",
            tags = "Admin - Clients"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Registration staged as PENDING, awaiting authorization",
                    content = @Content(schema = @Schema(implementation = ApprovalRequestResponse.class))
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
    @PreAuthorize("@adminGuard.canInitiateVendorActions(authentication)")
    @PostMapping("/clients")
    public ResponseEntity<ApprovalRequestResponse> createClient(
            @Valid @RequestBody InitiateVendorRegistrationRequest request,
            Authentication authentication
    ) {
        log.info("Initiating vendor registration: {}", request.getCompanyName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(approvalService.initiateVendorRegistration(authentication, request));
    }

    @Operation(
            summary = "Initiate an admin user creation (maker-checker step 1)",
            description = "Stages a staff administrator with the requested governance role. The account stays "
                    + "disabled until an authorizer approves it; only then is a generated temporary password "
                    + "emailed, to be changed at first sign-in. Requires the CONTROL or SUPER_ADMIN role.",
            tags = "Admin - Users"
    )
    @PreAuthorize("@adminGuard.canManageStaff(authentication)")
    @PostMapping("/users")
    public ResponseEntity<ApprovalRequestResponse> createBankUser(
            @Valid @RequestBody InitiateAdminUserRequest request,
            Authentication authentication
    ) {
        log.info("Initiating bank user creation: {}", request.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(approvalService.initiateAdminUserCreation(authentication, request));
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
    @PreAuthorize("@adminGuard.canAuthorize(authentication)")
    @PutMapping("/clients/{clientId}/approve")
    public ResponseEntity<ClientResponse> approveClient(
            @Parameter(description = "Client identifier") @PathVariable UUID clientId,
            @RequestBody ApproveClientRequest request,
            Authentication authentication
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
    @PreAuthorize("@adminGuard.canAuthorize(authentication)")
    @PutMapping("/clients/{clientId}/reject")
    public ResponseEntity<ClientResponse> rejectClient(
            @Parameter(description = "Client identifier") @PathVariable UUID clientId,
            Authentication authentication
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
    @PreAuthorize("@adminGuard.canInitiateVendorActions(authentication)")
    @PutMapping("/clients/{clientId}/suspend")
    public ResponseEntity<ApprovalRequestResponse> suspendClient(
            @Parameter(description = "Client identifier") @PathVariable UUID clientId,
            @Valid @RequestBody InitiateClientActionRequest request,
            Authentication authentication
    ) {
        request.setClientId(clientId);
        log.info("Initiating suspension of client: {}", clientId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(approvalService.initiateClientAction(
                        authentication, ApprovalActionType.CLIENT_SUSPEND, request));
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
    @PreAuthorize("@adminGuard.canInitiateVendorActions(authentication)")
    @PutMapping("/clients/{clientId}/activate")
    public ResponseEntity<ApprovalRequestResponse> activateClient(
            @Parameter(description = "Client identifier") @PathVariable UUID clientId,
            @Valid @RequestBody InitiateClientActionRequest request,
            Authentication authentication) {
        request.setClientId(clientId);
        log.info("Initiating activation of client: {}", clientId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(approvalService.initiateClientAction(
                        authentication, ApprovalActionType.CLIENT_ACTIVATE, request));
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
    @PreAuthorize("@adminGuard.canViewAudit(authentication)")
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
    @PreAuthorize("@adminGuard.canInitiateVendorActions(authentication)")
    @PutMapping("/users/{userId}/roles")
    public ResponseEntity<ApprovalRequestResponse> assignRoles(
            @Parameter(description = "Keycloak user id (the JWT `sub` claim)") @PathVariable String userId,
            @Valid @RequestBody UserRolesRequest request,
            Authentication authentication
    ) {
        InitiateServiceAssignmentRequest initiation = InitiateServiceAssignmentRequest.builder()
                .targetKeycloakUserId(userId)
                .roles(request.roles())
                .mode("GRANT")
                .comment(request.comment())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(approvalService.initiateServiceAssignment(authentication, initiation));
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
    @PreAuthorize("@adminGuard.canInitiateVendorActions(authentication)")
    @DeleteMapping("/users/{userId}/roles")
    public ResponseEntity<ApprovalRequestResponse> unassignRoles(
            @Parameter(description = "Keycloak user id (the JWT `sub` claim)") @PathVariable String userId,
            @Valid @RequestBody UserRolesRequest request,
            Authentication authentication
    ) {
        InitiateServiceAssignmentRequest initiation = InitiateServiceAssignmentRequest.builder()
                .targetKeycloakUserId(userId)
                .roles(request.roles())
                .mode("REVOKE")
                .comment(request.comment())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(approvalService.initiateServiceAssignment(authentication, initiation));
    }

}