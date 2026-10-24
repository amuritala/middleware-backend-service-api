package com.banking.api.controller;

import com.banking.api.dto.AdminMeResponse;
import com.banking.api.dto.AdminUserSummaryResponse;
import com.banking.api.dto.ApprovalDecisionRequest;
import com.banking.api.dto.ApprovalRequestResponse;
import com.banking.api.dto.AuditLogResponse;
import com.banking.api.dto.InitiateRoleAssignmentRequest;
import com.banking.api.dto.InitiateUserAccessRequest;
import com.banking.api.entity.AdminRole;
import com.banking.api.entity.ApprovalActionType;
import com.banking.api.entity.AuditLog;
import com.banking.api.service.AdminGuard;
import com.banking.api.service.AdminService;
import com.banking.api.service.ApprovalService;
import com.banking.api.service.AuditLogService;
import com.banking.api.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The user-management and maker-checker surface: who the signed-in administrator is, the staff
 * directory with governance roles, the approvals queue, and the governance audit trail. Every
 * method is guarded by {@code adminGuard} so a role change on the server is enforced even if
 * someone calls the API directly.
 */
@RestController
@RequestMapping(value = "/api/admin", produces = "application/json")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@SecurityRequirement(name = "keycloak")
@Tag(name = "Admin - Governance", description = "Maker-checker queue, staff governance roles and the audit trail.")
public class GovernanceController {

    private final AdminGuard adminGuard;
    private final AdminService adminService;
    private final ApprovalService approvalService;
    private final AuditLogService auditLogService;

    // ============================================================
    // Who am I
    // ============================================================

    @Operation(
            summary = "The signed-in administrator's governance profile",
            description = "Returns the local account's governance role (SUPER_ADMIN, CONTROL, INITIATOR, "
                    + "AUTHORIZER) plus the derived permission keys the frontend uses to decide which screens "
                    + "and buttons to render. Also reports whether the temporary password must still be changed.",
            tags = "Admin - Governance"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The caller's governance profile",
                    content = @Content(schema = @Schema(implementation = AdminMeResponse.class))),
            @ApiResponse(responseCode = "403", description = "No local account for this token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<AdminMeResponse> me(Authentication authentication) {
        return authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt
                ? ResponseEntity.ok(adminService.getMe(jwt.getSubject()))
                : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    // ============================================================
    // Staff directory with governance roles
    // ============================================================

    @Operation(
            summary = "List bank staff administrators",
            description = "Every staff account holding ROLE_ADMIN, newest first, with the governance role that "
                    + "decides what each may do. Control and Super Admin only.",
            tags = "Admin - Governance"
    )
    @PreAuthorize("@adminGuard.canManageStaff(authentication)")
    @GetMapping("/user-management/users")
    public ResponseEntity<List<AdminUserSummaryResponse>> getStaffUsers() {
        return ResponseEntity.ok(adminService.getStaffUsers());
    }

    @Operation(
            summary = "Initiate a governance role change (maker-checker step 1)",
            description = "Proposes that an existing staff member becomes a Control, Initiator or Authorizer. "
                    + "An authorizer must approve before the role applies. Control and Super Admin may propose; "
                    + "you cannot propose a change to your own role.",
            tags = "Admin - Governance"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Request staged as PENDING",
                    content = @Content(schema = @Schema(implementation = ApprovalRequestResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing comment, or a self role change",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not Control/Super Admin, or would "
                    + "grant/demote a Super Admin without holding that role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("@adminGuard.canManageStaff(authentication)")
    @PostMapping("/user-management/role-assignments")
    public ResponseEntity<ApprovalRequestResponse> initiateRoleAssignment(
            @Valid @RequestBody InitiateRoleAssignmentRequest request,
            Authentication authentication
    ) {
        log.info("Initiating governance role change for {} -> {}",
                request.getTargetKeycloakUserId(), request.getAdminRole());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(approvalService.initiateRoleAssignment(authentication, request));
    }

    @Operation(
            summary = "Suspend, reinstate or time-lock an account (maker-checker step 1)",
            description = "Proposes the access change; nothing happens until an authorizer approves it with a "
                    + "comment of their own. SUSPEND blocks sign-in and API access until an approved "
                    + "reinstatement; LOCK blocks both until `lockMinutes` passes, then access returns "
                    + "automatically. Control and Super Admin may propose, with a comment - self-service and "
                    + "changes to another Super Admin are refused.",
            tags = "Admin - Governance"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Request staged as PENDING",
                    content = @Content(schema = @Schema(implementation = ApprovalRequestResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing comment, self-service attempt or "
                    + "invalid lock duration",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not Control/Super Admin, or "
                    + "targeted a Super Admin without holding that role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No local user with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The account is already in the state the "
                    + "action would produce",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("@adminGuard.canManageStaff(authentication)")
    @PostMapping("/user-management/user-actions")
    public ResponseEntity<ApprovalRequestResponse> initiateUserAccess(
            @Valid @RequestBody InitiateUserAccessRequest request,
            Authentication authentication
    ) {
        log.info("Initiating user access action {} for {}",
                request.getAction(), request.getTargetKeycloakUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(approvalService.initiateUserAccess(authentication, request));
    }

    @Operation(
            summary = "The four governance roles and their permission keys",
            description = "Static description used by the User Management module to render role cards and the "
                    + "permission matrix.",
            tags = "Admin - Governance"
    )
    @GetMapping("/user-management/roles")
    public ResponseEntity<List<RoleDescription>> getGovernanceRoles() {
        return ResponseEntity.ok(Arrays.stream(AdminRole.values()).map(role -> new RoleDescription(
                role.name(),
                role.getLabel(),
                role.getDescription(),
                role.getPermissions()
        )).collect(Collectors.toList()));
    }

    public record RoleDescription(String role, String label, String description, List<String> permissions) {
    }

    // ============================================================
    // Maker-checker queue
    // ============================================================

    @Operation(
            summary = "The approvals queue",
            description = "Authorizers and Super Admins see every request; Initiators and Control see only the "
                    + "requests they initiated themselves. Filter with ?status=PENDING (default) or ALL.",
            tags = "Admin - Governance"
    )
    @GetMapping("/approvals")
    public ResponseEntity<List<ApprovalRequestResponse>> getApprovals(
            @Parameter(description = "PENDING, APPROVED, REJECTED or ALL")
            @RequestParam(required = false) String status,
            Authentication authentication
    ) {
        return ResponseEntity.ok(approvalService.listRequests(authentication, status));
    }

    @Operation(
            summary = "One approval request",
            description = "Full detail of a single maker-checker request.",
            tags = "Admin - Governance"
    )
    @GetMapping("/approvals/{requestId}")
    public ResponseEntity<ApprovalRequestResponse> getApproval(
            @PathVariable UUID requestId, Authentication authentication) {
        return ResponseEntity.ok(approvalService.getRequest(authentication, requestId));
    }

    @Operation(
            summary = "Authorize a request (maker-checker step 2)",
            description = "Executes the proposed action and marks the request APPROVED. The four-eyes rule is "
                    + "enforced: you can never authorize a request you initiated yourself, Super Admin included. "
                    + "Most action types require a comment.",
            tags = "Admin - Governance"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request approved and executed",
                    content = @Content(schema = @Schema(implementation = ApprovalRequestResponse.class))),
            @ApiResponse(responseCode = "400", description = "Comment required for this action type",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller cannot authorize, or initiated this request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Request already decided",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("@adminGuard.canAuthorize(authentication)")
    @PutMapping("/approvals/{requestId}/approve")
    public ResponseEntity<ApprovalRequestResponse> approve(
            @PathVariable UUID requestId,
            @RequestBody ApprovalDecisionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(approvalService.decide(authentication, requestId, request, true));
    }

    @Operation(
            summary = "Refuse a request",
            description = "Marks the request REJECTED; the proposed action never runs and any staged account "
                    + "stays disabled. The four-eyes rule applies here too.",
            tags = "Admin - Governance"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request rejected",
                    content = @Content(schema = @Schema(implementation = ApprovalRequestResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller cannot authorize, or initiated this request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Request already decided",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("@adminGuard.canAuthorize(authentication)")
    @PutMapping("/approvals/{requestId}/reject")
    public ResponseEntity<ApprovalRequestResponse> reject(
            @PathVariable UUID requestId,
            @RequestBody ApprovalDecisionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(approvalService.decide(authentication, requestId, request, false));
    }

    // ============================================================
    // Governance audit trail
    // ============================================================

    @Operation(
            summary = "Page through the governance audit log",
            description = "Every initiation, approval and rejection: who acted, in which role, on what, and why. "
                    + "Spring `Page` envelope, newest first. Super Admin only.",
            tags = "Admin - Governance"
    )
    @PreAuthorize("@adminGuard.canViewAudit(authentication)")
    @GetMapping("/audit")
    public ResponseEntity<Page<AuditLogResponse>> getAudit(
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(auditLogService.findAll(pageable).map(GovernanceController::toAuditResponse));
    }

    private static AuditLogResponse toAuditResponse(AuditLog entry) {
        return new AuditLogResponse(
                entry.getId(),
                entry.getActionType(),
                entry.getOutcome().name(),
                entry.getSummary(),
                entry.getDetail(),
                entry.getTargetLabel(),
                entry.getActorName(),
                entry.getActorEmail(),
                entry.getActorRole(),
                entry.getComment(),
                entry.getCreatedAt()
        );
    }
}
