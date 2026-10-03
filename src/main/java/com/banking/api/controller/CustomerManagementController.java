package com.banking.api.controller;

import com.banking.api.dto.RoleChangeApplicationRequest;
import com.banking.api.dto.RoleChangeApplicationResponse;
import com.banking.api.exception.ErrorResponse;
import com.banking.api.service.CustomerManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/customer-management", produces = "application/json")
@RequiredArgsConstructor
@SecurityRequirement(name = "keycloak")
public class CustomerManagementController {

    private final CustomerManagementService customerManagementService;

    @Operation(
            summary = "Submit a role-change application",
            description = "A vendor asks for a different set of service roles. Only one application may be pending at a "
                    + "time, and ROLE_ADMIN / ROLE_CLIENT cannot be requested.",
            tags = "Customer - Role Applications"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Application recorded with status PENDING",
                    content = @Content(schema = @Schema(implementation = RoleChangeApplicationResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "`requestedRoles` is missing or empty, a system role was requested, or a role "
                            + "name is unknown",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_CLIENT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "This vendor already has a role-change application pending",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/role-applications")
    @PreAuthorize("hasAuthority('ROLE_CLIENT')")
    public ResponseEntity<RoleChangeApplicationResponse> submitRoleChange(
            Authentication authentication,
            @Valid @RequestBody RoleChangeApplicationRequest request
    ) {
        return ResponseEntity.ok(customerManagementService.submitRoleChange(subject(authentication), request));
    }

    @Operation(
            summary = "List my role-change applications",
            description = "Every application submitted by the calling user, newest first - use it to show request status.",
            tags = "Customer - Role Applications"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "The caller's applications",
                    content = @Content(
                            array = @ArraySchema(schema = @Schema(implementation = RoleChangeApplicationResponse.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_CLIENT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/role-applications/mine")
    @PreAuthorize("hasAuthority('ROLE_CLIENT')")
    public ResponseEntity<List<RoleChangeApplicationResponse>> getMyApplications(Authentication authentication) {
        return ResponseEntity.ok(customerManagementService.getMyApplications(subject(authentication)));
    }

    @Operation(
            summary = "List pending role-change applications",
            description = "The administrator's review queue: all applications still in PENDING status.",
            tags = "Customer - Role Applications"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Applications awaiting review",
                    content = @Content(
                            array = @ArraySchema(schema = @Schema(implementation = RoleChangeApplicationResponse.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/role-applications/pending")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<RoleChangeApplicationResponse>> getPendingApplications() {
        return ResponseEntity.ok(customerManagementService.getPendingApplications());
    }

    @Operation(
            summary = "Get one role-change application",
            description = "Full detail of an application, including who reviewed it and when.",
            tags = "Customer - Role Applications"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "The requested application",
                    content = @Content(schema = @Schema(implementation = RoleChangeApplicationResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No application exists with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/role-applications/{applicationId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RoleChangeApplicationResponse> getApplication(
            @Parameter(description = "Application identifier") @PathVariable UUID applicationId
    ) {
        return ResponseEntity.ok(customerManagementService.getApplication(applicationId));
    }

    @Operation(
            summary = "Approve a role-change application",
            description = "Applies the requested roles to the vendor in Keycloak and marks the application APPROVED, "
                    + "recording the reviewing administrator.",
            tags = "Customer - Role Applications"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Application approved",
                    content = @Content(schema = @Schema(implementation = RoleChangeApplicationResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No application exists with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The application was already approved or rejected",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PutMapping("/role-applications/{applicationId}/approve")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RoleChangeApplicationResponse> approveApplication(
            Authentication authentication,
            @Parameter(description = "Application identifier") @PathVariable UUID applicationId
    ) {
        return ResponseEntity.ok(customerManagementService.approveApplication(
                applicationId,
                subject(authentication)
        ));
    }

    @Operation(
            summary = "Reject a role-change application",
            description = "Marks the application REJECTED without changing the vendor's current roles.",
            tags = "Customer - Role Applications"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Application rejected",
                    content = @Content(schema = @Schema(implementation = RoleChangeApplicationResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No application exists with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The application was already approved or rejected",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PutMapping("/role-applications/{applicationId}/reject")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RoleChangeApplicationResponse> rejectApplication(
            Authentication authentication,
            @Parameter(description = "Application identifier") @PathVariable UUID applicationId
    ) {
        return ResponseEntity.ok(customerManagementService.rejectApplication(
                applicationId,
                subject(authentication)
        ));
    }

    private String subject(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("JWT authentication is required");
        }
        return jwt.getSubject();
    }
}
