package com.banking.api.service;

import com.banking.api.dto.AdminRoleAssignPayload;
import com.banking.api.dto.AdminUserCreatePayload;
import com.banking.api.dto.ApprovalDecisionRequest;
import com.banking.api.dto.ApprovalRequestResponse;
import com.banking.api.dto.ClientActionPayload;
import com.banking.api.dto.InitiateAdminUserRequest;
import com.banking.api.dto.InitiateClientActionRequest;
import com.banking.api.dto.InitiateRoleAssignmentRequest;
import com.banking.api.dto.InitiateUserAccessRequest;
import com.banking.api.dto.InitiateServiceAssignmentRequest;
import com.banking.api.dto.InitiateVendorRegistrationRequest;
import com.banking.api.dto.RegisterRequest;
import com.banking.api.dto.ServiceAssignmentPayload;
import com.banking.api.entity.AdminRole;
import com.banking.api.entity.ApprovalActionType;
import com.banking.api.entity.ApprovalRequest;
import com.banking.api.entity.ApprovalStatus;
import com.banking.api.entity.AuditOutcome;
import com.banking.api.entity.Client;
import com.banking.api.entity.User;
import com.banking.api.exception.ApiException;
import com.banking.api.repository.ApprovalRequestRepository;
import com.banking.api.repository.ClientRepository;
import com.banking.api.repository.UserRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The maker-checker engine. An initiator stages an action as a PENDING
 * {@link ApprovalRequest}; an authorizer decides it. The four-eyes rule is enforced here and
 * nowhere else matters: whoever initiated a request can never sign it off - Super Admin
 * included - and the strict action types refuse to move without a written comment from both
 * sides. Every transition lands in the audit trail.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalService {

    /** Upper bound on a proposed time lock - seven days, in minutes. */
    public static final int MAX_LOCK_MINUTES = 10_080;

    private final ApprovalRequestRepository approvalRequestRepository;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final AdminService adminService;
    private final AdminGuard adminGuard;
    private final AuditLogService auditLogService;

    /**
     * Instantiated rather than injected: Spring Boot 4 no longer auto-configures a
     * `com.fasterxml` ObjectMapper bean, and the service only ever round-trips its own
     * payload maps.
     */
    /**
     * Instantiated rather than injected: Spring Boot 4 no longer auto-configures a
     * `com.fasterxml` ObjectMapper bean. Stored payloads also carry context beyond the
     * target record (ids and statuses shown on the approval screen), so unknown
     * properties are ignored when a payload is read back into its typed record.
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // ============================================================
    // Initiation (the maker)
    // ============================================================

    @Transactional
    public ApprovalRequestResponse initiateVendorRegistration(
            Authentication authentication, InitiateVendorRegistrationRequest request) {
        User actor = adminGuard.requireGovernanceAdmin(authentication);
        requireCommentIfRequired(ApprovalActionType.VENDOR_REGISTRATION, request.getComment());

        RegisterRequest credentials = toRegisterRequest(request);
        Client client = adminService.provisionClientPending(credentials);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientId", client.getId().toString());
        payload.put("companyName", client.getCompanyName());
        payload.put("registrationNumber", client.getRegistrationNumber());
        payload.put("contactFirstName", client.getContactFirstName());
        payload.put("contactLastName", client.getContactLastName());
        payload.put("contactEmail", client.getEmail());
        payload.put("contactPhone", client.getContactPhone());
        payload.put("phoneNumber", client.getPhone());
        payload.put("country", client.getCountry());
        payload.put("taxId", client.getTaxId());
        payload.put("industry", client.getIndustry());
        payload.put("address", client.getAddress());

        ApprovalRequest saved = saveInitiation(
                actor, ApprovalActionType.VENDOR_REGISTRATION, payload,
                client.getCompanyName(), client.getId().toString(), request.getComment());

        auditLogService.record(
                ApprovalActionType.VENDOR_REGISTRATION.name(), AuditOutcome.INITIATED,
                "Vendor registration initiated for " + client.getCompanyName(),
                toJson(payload), client.getCompanyName(),
                actor.getKeycloakUserId(), displayName(actor), actor.getEmail(),
                roleLabel(actor), request.getComment());

        return toResponse(saved);
    }

    @Transactional
    public ApprovalRequestResponse initiateAdminUserCreation(
            Authentication authentication, InitiateAdminUserRequest request) {
        User actor = adminGuard.requireGovernanceAdmin(authentication);
        requireCommentIfRequired(ApprovalActionType.ADMIN_USER_CREATE, request.getComment());

        User staged = adminService.provisionBankUserPending(request);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", staged.getId().toString());
        payload.put("firstName", request.getFirstName());
        payload.put("lastName", request.getLastName());
        payload.put("email", request.getEmail());
        payload.put("adminRole", request.getAdminRole().name());

        ApprovalRequest saved = saveInitiation(
                actor, ApprovalActionType.ADMIN_USER_CREATE, payload,
                request.getEmail(), staged.getId().toString(), request.getComment());

        auditLogService.record(
                ApprovalActionType.ADMIN_USER_CREATE.name(), AuditOutcome.INITIATED,
                "Admin user creation initiated for " + request.getEmail()
                        + " (" + request.getAdminRole().getLabel() + ")",
                toJson(payload), request.getEmail(),
                actor.getKeycloakUserId(), displayName(actor), actor.getEmail(),
                roleLabel(actor), request.getComment());

        return toResponse(saved);
    }

    @Transactional
    public ApprovalRequestResponse initiateServiceAssignment(
            Authentication authentication, InitiateServiceAssignmentRequest request) {
        User actor = adminGuard.requireGovernanceAdmin(authentication);
        requireCommentIfRequired(ApprovalActionType.SERVICE_ASSIGNMENT, request.getComment());

        String mode = request.getMode() == null
                ? ""
                : request.getMode().trim().toUpperCase();
        if (!mode.equals("GRANT") && !mode.equals("REVOKE")) {
            throw new ApiException("INVALID_MODE", "Mode must be GRANT or REVOKE", HttpStatus.BAD_REQUEST);
        }

        // Fail at initiation if the target does not exist - never at authorization time.
        String targetLabel = request.getTargetLabel();
        if (userRepository.findByKeycloakUserId(request.getTargetKeycloakUserId()).isEmpty()
                && adminService.getKeycloakUserOrNull(request.getTargetKeycloakUserId()) == null) {
            throw new ApiException("TARGET_NOT_FOUND", "No user exists with that id", HttpStatus.NOT_FOUND);
        }
        if (targetLabel == null || targetLabel.isBlank()) {
            targetLabel = userRepository.findByKeycloakUserId(request.getTargetKeycloakUserId())
                    .map(User::getEmail)
                    .orElse(request.getTargetKeycloakUserId());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetKeycloakUserId", request.getTargetKeycloakUserId());
        payload.put("targetLabel", targetLabel);
        payload.put("roles", request.getRoles());
        payload.put("mode", mode);

        ApprovalRequest saved = saveInitiation(
                actor, ApprovalActionType.SERVICE_ASSIGNMENT, payload,
                targetLabel, request.getTargetKeycloakUserId(), request.getComment());

        auditLogService.record(
                ApprovalActionType.SERVICE_ASSIGNMENT.name(), AuditOutcome.INITIATED,
                mode + " of services " + request.getRoles() + " initiated for " + targetLabel,
                toJson(payload), targetLabel,
                actor.getKeycloakUserId(), displayName(actor), actor.getEmail(),
                roleLabel(actor), request.getComment());

        return toResponse(saved);
    }

    @Transactional
    public ApprovalRequestResponse initiateClientAction(
            Authentication authentication, ApprovalActionType actionType,
            InitiateClientActionRequest request) {
        if (actionType != ApprovalActionType.CLIENT_SUSPEND
                && actionType != ApprovalActionType.CLIENT_ACTIVATE) {
            throw new ApiException("INVALID_ACTION", "Unsupported client action", HttpStatus.BAD_REQUEST);
        }
        User actor = adminGuard.requireGovernanceAdmin(authentication);
        requireCommentIfRequired(actionType, request.getComment());

        Client client = clientRepository.findById(request.getClientId())
                .orElseThrow(() -> new ApiException(
                        "CLIENT_NOT_FOUND", "No client exists with that id", HttpStatus.NOT_FOUND));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientId", client.getId().toString());
        payload.put("companyName", client.getCompanyName());
        payload.put("roles", request.getRoles() == null ? List.of() : request.getRoles());
        payload.put("currentStatus", client.getStatus().name());

        ApprovalRequest saved = saveInitiation(
                actor, actionType, payload,
                client.getCompanyName(), client.getId().toString(), request.getComment());

        auditLogService.record(
                actionType.name(), AuditOutcome.INITIATED,
                actionType.getLabel() + " initiated for " + client.getCompanyName(),
                toJson(payload), client.getCompanyName(),
                actor.getKeycloakUserId(), displayName(actor), actor.getEmail(),
                roleLabel(actor), request.getComment());

        return toResponse(saved);
    }

    @Transactional
    public ApprovalRequestResponse initiateRoleAssignment(
            Authentication authentication, InitiateRoleAssignmentRequest request) {
        User actor = adminGuard.requireGovernanceAdmin(authentication);
        requireCommentIfRequired(ApprovalActionType.ADMIN_ROLE_ASSIGN, request.getComment());

        User target = userRepository.findByKeycloakUserId(request.getTargetKeycloakUserId())
                .orElseThrow(() -> new ApiException(
                        "TARGET_NOT_FOUND", "No user exists with that id", HttpStatus.NOT_FOUND));

        if (target.getKeycloakUserId().equals(actor.getKeycloakUserId())) {
            throw new ApiException(
                    "SELF_ROLE_CHANGE",
                    "You cannot change your own governance role",
                    HttpStatus.BAD_REQUEST);
        }
        boolean actorIsSuperAdmin = actor.getAdminRole() == AdminRole.SUPER_ADMIN;
        if (!actorIsSuperAdmin && request.getAdminRole() == AdminRole.SUPER_ADMIN) {
            throw new ApiException(
                    "SUPER_ADMIN_PROTECTED",
                    "Only a Super Admin can grant the Super Admin role",
                    HttpStatus.FORBIDDEN);
        }
        if (!actorIsSuperAdmin && target.getAdminRole() == AdminRole.SUPER_ADMIN) {
            throw new ApiException(
                    "SUPER_ADMIN_PROTECTED",
                    "Only a Super Admin can change the role of another Super Admin",
                    HttpStatus.FORBIDDEN);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", target.getId().toString());
        payload.put("targetKeycloakUserId", target.getKeycloakUserId());
        payload.put("targetLabel", target.getEmail());
        payload.put("currentRole", target.getAdminRole() == null ? null : target.getAdminRole().name());
        payload.put("adminRole", request.getAdminRole().name());

        ApprovalRequest saved = saveInitiation(
                actor, ApprovalActionType.ADMIN_ROLE_ASSIGN, payload,
                target.getEmail(), target.getId().toString(), request.getComment());

        auditLogService.record(
                ApprovalActionType.ADMIN_ROLE_ASSIGN.name(), AuditOutcome.INITIATED,
                "Role change to " + request.getAdminRole().getLabel() + " initiated for "
                        + displayName(target) + " (" + target.getEmail() + ")",
                toJson(payload), target.getEmail(),
                actor.getKeycloakUserId(), displayName(actor), actor.getEmail(),
                roleLabel(actor), request.getComment());

        return toResponse(saved);
    }

    @Transactional
    public ApprovalRequestResponse initiateUserAccess(
            Authentication authentication, InitiateUserAccessRequest request) {
        User actor = adminGuard.requireGovernanceAdmin(authentication);
        ApprovalActionType actionType = actionTypeFor(request.getAction());
        requireCommentIfRequired(actionType, request.getComment());

        User target = userRepository.findByKeycloakUserId(request.getTargetKeycloakUserId())
                .orElseThrow(() -> new ApiException(
                        "TARGET_NOT_FOUND", "No user exists with that id", HttpStatus.NOT_FOUND));

        if (target.getKeycloakUserId().equals(actor.getKeycloakUserId())) {
            throw new ApiException(
                    "SELF_ACCESS_CHANGE",
                    "You cannot suspend, reinstate or lock your own account",
                    HttpStatus.BAD_REQUEST);
        }
        boolean actorIsSuperAdmin = actor.getAdminRole() == AdminRole.SUPER_ADMIN;
        if (!actorIsSuperAdmin && target.getAdminRole() == AdminRole.SUPER_ADMIN) {
            throw new ApiException(
                    "SUPER_ADMIN_PROTECTED",
                    "Only a Super Admin can change the access of another Super Admin",
                    HttpStatus.FORBIDDEN);
        }

        boolean suspended = Boolean.TRUE.equals(target.getSuspended());
        switch (request.getAction()) {
            case SUSPEND -> {
                if (suspended) {
                    throw new ApiException(
                            "ALREADY_SUSPENDED",
                            "That account is already suspended - propose a reinstatement instead",
                            HttpStatus.CONFLICT);
                }
            }
            case UNSUSPEND -> {
                if (!suspended) {
                    throw new ApiException(
                            "NOT_SUSPENDED",
                            "That account is not suspended, so there is nothing to reinstate",
                            HttpStatus.CONFLICT);
                }
            }
            case LOCK -> {
                if (request.getLockMinutes() == null
                        || request.getLockMinutes() < 1
                        || request.getLockMinutes() > MAX_LOCK_MINUTES) {
                    throw new ApiException(
                            "INVALID_LOCK_MINUTES",
                            "The lock must last between 1 and " + MAX_LOCK_MINUTES + " minutes",
                            HttpStatus.BAD_REQUEST);
                }
                if (suspended) {
                    throw new ApiException(
                            "ACCOUNT_SUSPENDED",
                            "That account is already suspended - a time lock would change nothing",
                            HttpStatus.CONFLICT);
                }
            }
        }

        String targetLabel = request.getTargetLabel() == null || request.getTargetLabel().isBlank()
                ? target.getEmail()
                : request.getTargetLabel().trim();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", request.getAction().name());
        payload.put("userId", target.getId().toString());
        payload.put("targetKeycloakUserId", target.getKeycloakUserId());
        payload.put("targetLabel", targetLabel);
        if (request.getAction() == InitiateUserAccessRequest.Action.LOCK) {
            payload.put("lockMinutes", request.getLockMinutes());
        }
        payload.put("currentSuspended", suspended);

        ApprovalRequest saved = saveInitiation(
                actor, actionType, payload,
                targetLabel, target.getId().toString(), request.getComment());

        auditLogService.record(
                actionType.name(), AuditOutcome.INITIATED,
                actionType.getLabel() + " (" + request.getAction().name() + ") initiated for "
                        + targetLabel
                        + (request.getAction() == InitiateUserAccessRequest.Action.LOCK
                                ? " - " + request.getLockMinutes() + " minutes"
                                : ""),
                toJson(payload), targetLabel,
                actor.getKeycloakUserId(), displayName(actor), actor.getEmail(),
                roleLabel(actor), request.getComment());

        return toResponse(saved);
    }

    // ============================================================
    // Authorization (the checker)
    // ============================================================

    /**
     * Signs off or refuses a pending request. The four-eyes rule lives here: an authorizer
     * can never decide a request they initiated themselves - including Super Admins.
     */
    @Transactional
    public ApprovalRequestResponse decide(
            Authentication authentication, UUID requestId,
            ApprovalDecisionRequest decision, boolean approve) {

        User actor = adminGuard.requireGovernanceAdmin(authentication);
        ApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new ApiException(
                        "REQUEST_NOT_FOUND", "No approval request exists with that id",
                        HttpStatus.NOT_FOUND));

        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new ApiException(
                    "ALREADY_DECIDED",
                    "This request was already " + request.getStatus().name().toLowerCase(),
                    HttpStatus.CONFLICT);
        }

        if (request.getInitiatedBy() != null
                && request.getInitiatedBy().equals(actor.getKeycloakUserId())) {
            throw new ApiException(
                    "FOUR_EYES_FORBIDDEN",
                    "You cannot authorize an action you initiated yourself - ask another authorizer or Super Admin to review it.",
                    HttpStatus.FORBIDDEN);
        }

        String comment = decision == null || decision.getComment() == null
                ? null
                : decision.getComment().trim();
        if (comment != null && comment.isBlank()) {
            comment = null;
        }
        requireCommentIfRequired(request.getActionType(), comment);

        if (approve) {
            executeApproval(request);
            request.approve(actor.getKeycloakUserId(), displayName(actor),
                    roleLabel(actor), comment);
        } else {
            if (comment == null) {
                // Rejections always carry a reason so the initiator knows what to fix.
                comment = "Rejected";
            }
            executeRejection(request);
            request.reject(actor.getKeycloakUserId(), displayName(actor),
                    roleLabel(actor), comment);
        }

        ApprovalRequest saved = approvalRequestRepository.save(request);

        auditLogService.record(
                request.getActionType().name(),
                approve ? AuditOutcome.APPROVED : AuditOutcome.REJECTED,
                request.getActionType().getLabel() + (approve ? " approved for " : " rejected for ")
                        + request.getTargetLabel(),
                request.getPayload(), request.getTargetLabel(),
                actor.getKeycloakUserId(), displayName(actor), actor.getEmail(),
                roleLabel(actor), comment);

        log.info("{} {} '{}' by {}", approve ? "Approved" : "Rejected",
                request.getActionType(), request.getTargetLabel(), actor.getEmail());
        return toResponse(saved);
    }

    // ============================================================
    // Queries
    // ============================================================

    /**
     * The queue for the calling administrator: authorizers and super admins see everything,
     * initiators and control see only what they proposed themselves.
     */
    @Transactional
    public List<ApprovalRequestResponse> listRequests(Authentication authentication, String status) {
        User actor = adminGuard.requireGovernanceAdmin(authentication);

        List<ApprovalRequest> requests;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            requests = approvalRequestRepository.findAllByOrderByCreatedAtDesc();
        } else {
            requests = approvalRequestRepository.findByStatusOrderByCreatedAtDesc(
                    ApprovalStatus.valueOf(status.trim().toUpperCase()));
        }

        boolean seesAll = actor.getAdminRole() == AdminRole.SUPER_ADMIN
                || actor.getAdminRole() == AdminRole.AUTHORIZER;
        return requests.stream()
                .filter(request -> seesAll
                        || actor.getKeycloakUserId().equals(request.getInitiatedBy()))
                .map(this::toResponse)
                .toList();
    }

    public ApprovalRequestResponse getRequest(Authentication authentication, UUID requestId) {
        adminGuard.requireGovernanceAdmin(authentication);
        ApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new ApiException(
                        "REQUEST_NOT_FOUND", "No approval request exists with that id",
                        HttpStatus.NOT_FOUND));
        return toResponse(request);
    }

    /** Counts for the module header: how much work is waiting on an authorizer. */
    @Transactional
    public long countPending() {
        return approvalRequestRepository.findByStatusOrderByCreatedAtDesc(ApprovalStatus.PENDING).size();
    }

    // ============================================================
    // Internals
    // ============================================================

    private ApprovalRequest saveInitiation(
            User actor,
            ApprovalActionType actionType,
            Map<String, Object> payload,
            String targetLabel,
            String targetId,
            String comment
    ) {
        return approvalRequestRepository.save(ApprovalRequest.builder()
                .actionType(actionType)
                .payload(toJson(payload))
                .targetLabel(targetLabel)
                .targetId(targetId)
                .status(ApprovalStatus.PENDING)
                .initiatedBy(actor.getKeycloakUserId())
                .initiatedByName(displayName(actor))
                .initiatedByEmail(actor.getEmail())
                .initiatedByRole(roleLabel(actor))
                .initiatedComment(comment)
                .build());
    }

    /** Runs the approved action. Any failure rolls the decision back - the queue keeps it pending. */
    private void executeApproval(ApprovalRequest request) {
        Map<String, Object> payload = parsePayload(request.getPayload());
        switch (request.getActionType()) {
            case VENDOR_REGISTRATION -> adminService.completeClientApproval(
                    UUID.fromString(stringValue(payload.get("clientId"))));
            case ADMIN_USER_CREATE -> adminService.completeBankUserApproval(
                    UUID.fromString(stringValue(payload.get("userId"))));
            case SERVICE_ASSIGNMENT -> {
                ServiceAssignmentPayload detail = objectMapper.convertValue(payload, ServiceAssignmentPayload.class);
                adminService.applyServiceRoles(
                        detail.targetKeycloakUserId(), detail.roles(), detail.mode());
            }
            case CLIENT_SUSPEND -> adminService.suspendClient(
                    UUID.fromString(stringValue(payload.get("clientId"))));
            case CLIENT_ACTIVATE -> {
                ClientActionPayload detail = objectMapper.convertValue(payload, ClientActionPayload.class);
                List<String> roles = detail.roles() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(detail.roles());
                adminService.activateClient(detail.clientId(), roles);
            }
            case ADMIN_ROLE_ASSIGN -> {
                AdminRoleAssignPayload detail = objectMapper.convertValue(payload, AdminRoleAssignPayload.class);
                User target = userRepository.findByKeycloakUserId(detail.targetKeycloakUserId())
                        .orElseThrow(() -> new ApiException(
                                "TARGET_NOT_FOUND", "No user exists with that id", HttpStatus.NOT_FOUND));
                adminService.applyAdminRole(target.getId(), detail.adminRole());
            }
            case USER_SUSPEND -> adminService.suspendUser(
                    stringValue(payload.get("targetKeycloakUserId")));
            case USER_UNSUSPEND -> adminService.unsuspendUser(
                    stringValue(payload.get("targetKeycloakUserId")));
            case USER_LOCK -> adminService.lockUser(
                    stringValue(payload.get("targetKeycloakUserId")),
                    intValue(payload.get("lockMinutes")));
        }
    }

    /** Whatever the refusal means for the staged records. Staged accounts simply stay disabled. */
    private void executeRejection(ApprovalRequest request) {
        Map<String, Object> payload = parsePayload(request.getPayload());
        switch (request.getActionType()) {
            case VENDOR_REGISTRATION -> adminService.completeClientRejection(
                    UUID.fromString(stringValue(payload.get("clientId"))));
            case ADMIN_USER_CREATE -> adminService.completeBankUserRejection(
                    UUID.fromString(stringValue(payload.get("userId"))));
            default -> {
                // Nothing has happened yet for role, service and lifecycle requests:
                // refusal simply means the proposed change never runs.
            }
        }
    }

    private void requireCommentIfRequired(ApprovalActionType actionType, String comment) {
        if (actionType.isCommentRequired() && (comment == null || comment.isBlank())) {
            throw new ApiException(
                    "COMMENT_REQUIRED",
                    "A comment is required for " + actionType.getLabel().toLowerCase() + " actions",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private Map<String, Object> parsePayload(String json) {
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (Exception e) {
            throw new ApiException("PAYLOAD_UNREADABLE", "Stored request payload is unreadable",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise approval payload", e);
        }
    }

    private ApprovalRequestResponse toResponse(ApprovalRequest request) {
        return new ApprovalRequestResponse(
                request.getId(),
                request.getActionType().name(),
                request.getActionType().getLabel(),
                request.getActionType().isCommentRequired(),
                request.getTargetLabel(),
                request.getTargetId(),
                request.getStatus(),
                parsePayload(request.getPayload()),
                request.getInitiatedBy(),
                request.getInitiatedByName(),
                request.getInitiatedByEmail(),
                request.getInitiatedByRole(),
                request.getInitiatedComment(),
                request.getAuthorizedBy(),
                request.getAuthorizedByName(),
                request.getAuthorizedByRole(),
                request.getAuthorizationComment(),
                request.getAuthorizedAt(),
                request.getCreatedAt()
        );
    }

    private String displayName(User user) {
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isEmpty() ? user.getEmail() : name;
    }

    private String roleLabel(User user) {
        return user.getAdminRole() == null ? "UNCLASSIFIED" : user.getAdminRole().name();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(stringValue(value));
        } catch (NumberFormatException exception) {
            throw new ApiException(
                    "INVALID_LOCK_MINUTES", "Stored lock duration is unreadable",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private ApprovalActionType actionTypeFor(InitiateUserAccessRequest.Action action) {
        return switch (action) {
            case SUSPEND -> ApprovalActionType.USER_SUSPEND;
            case UNSUSPEND -> ApprovalActionType.USER_UNSUSPEND;
            case LOCK -> ApprovalActionType.USER_LOCK;
        };
    }

    private RegisterRequest toRegisterRequest(InitiateVendorRegistrationRequest request) {
        return RegisterRequest.builder()
                .companyName(request.getCompanyName())
                .registrationNumber(request.getRegistrationNumber())
                .contactFirstName(request.getContactFirstName())
                .contactLastName(request.getContactLastName())
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .phoneNumber(request.getPhoneNumber())
                .country(request.getCountry())
                .taxId(request.getTaxId())
                .companyAddress(request.getCompanyAddress())
                .industry(request.getIndustry())
                // Never used: the credential is generated when the authorizer approves.
                .password("unused-at-initiation")
                .build();
    }
}
