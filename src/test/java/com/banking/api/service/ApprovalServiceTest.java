package com.banking.api.service;

import com.banking.api.dto.ApprovalDecisionRequest;
import com.banking.api.dto.ApprovalRequestResponse;
import com.banking.api.entity.AdminRole;
import com.banking.api.entity.ApprovalActionType;
import com.banking.api.entity.ApprovalRequest;
import com.banking.api.entity.ApprovalStatus;
import com.banking.api.entity.AuditOutcome;
import com.banking.api.entity.Role;
import com.banking.api.entity.User;
import com.banking.api.exception.ApiException;
import com.banking.api.repository.ApprovalRequestRepository;
import com.banking.api.repository.ClientRepository;
import com.banking.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The maker-checker contract: nobody signs off their own request, the strict action types
 * refuse to move without a comment, every decision is audited, and the queue shows each
 * administrator only what they are allowed to see.
 */
class ApprovalServiceTest {

    private ApprovalRequestRepository approvalRequestRepository;
    private UserRepository userRepository;
    private ClientRepository clientRepository;
    private AdminService adminService;
    private AdminGuard adminGuard;
    private AuditLogService auditLogService;
    private ApprovalService approvalService;

    private Authentication authentication;

    private static final String AUTHORIZER_KC = "kc-authorizer";
    private static final String INITIATOR_KC = "kc-initiator";

    @BeforeEach
    void setUp() {
        approvalRequestRepository = mock(ApprovalRequestRepository.class);
        userRepository = mock(UserRepository.class);
        clientRepository = mock(ClientRepository.class);
        adminService = mock(AdminService.class);
        adminGuard = mock(AdminGuard.class);
        auditLogService = mock(AuditLogService.class);
        authentication = mock(Authentication.class);

        approvalService = new ApprovalService(
                approvalRequestRepository,
                userRepository,
                clientRepository,
                adminService,
                adminGuard,
                auditLogService);

        when(approvalRequestRepository.save(any(ApprovalRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // === Four-eyes ===

    @Test
    void authorizerCannotApproveTheirOwnRequest() {
        User actor = actor(AUTHORIZER_KC, AdminRole.AUTHORIZER);
        when(adminGuard.requireGovernanceAdmin(authentication)).thenReturn(actor);

        ApprovalRequest own = pending(ApprovalActionType.SERVICE_ASSIGNMENT, AUTHORIZER_KC);
        when(approvalRequestRepository.findById(own.getId())).thenReturn(Optional.of(own));

        ApiException error = assertThrows(ApiException.class, () -> approvalService.decide(
                authentication, own.getId(), new ApprovalDecisionRequest("looks fine"), true));

        assertEquals("FOUR_EYES_FORBIDDEN", error.getErrorCode());
        assertEquals(403, error.getStatus().value());
        verify(auditLogService, never()).record(anyString(), any(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void superAdminCannotAuthorizeTheirOwnRequestEither() {
        User actor = actor(AUTHORIZER_KC, AdminRole.SUPER_ADMIN);
        when(adminGuard.requireGovernanceAdmin(authentication)).thenReturn(actor);

        ApprovalRequest own = pending(ApprovalActionType.VENDOR_REGISTRATION, AUTHORIZER_KC);
        when(approvalRequestRepository.findById(own.getId())).thenReturn(Optional.of(own));

        ApiException error = assertThrows(ApiException.class, () -> approvalService.decide(
                authentication, own.getId(), new ApprovalDecisionRequest(null), true));

        assertEquals("FOUR_EYES_FORBIDDEN", error.getErrorCode());
    }

    // === Comment rules ===

    @Test
    void serviceAssignmentRefusesToAuthorizeWithoutAComment() {
        User actor = actor(AUTHORIZER_KC, AdminRole.AUTHORIZER);
        when(adminGuard.requireGovernanceAdmin(authentication)).thenReturn(actor);

        ApprovalRequest request = pending(ApprovalActionType.SERVICE_ASSIGNMENT, INITIATOR_KC);
        when(approvalRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        ApiException error = assertThrows(ApiException.class, () -> approvalService.decide(
                authentication, request.getId(), new ApprovalDecisionRequest("   "), true));

        assertEquals("COMMENT_REQUIRED", error.getErrorCode());
        assertEquals(ApprovalStatus.PENDING, request.getStatus());
    }

    @Test
    void vendorRegistrationMayBeAuthorizedWithoutAComment() {
        User actor = actor(AUTHORIZER_KC, AdminRole.AUTHORIZER);
        when(adminGuard.requireGovernanceAdmin(authentication)).thenReturn(actor);

        ApprovalRequest request = pending(ApprovalActionType.VENDOR_REGISTRATION, INITIATOR_KC);
        request.setPayload("{\"clientId\":\"" + request.getTargetId() + "\"}");
        when(approvalRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        ApprovalRequestResponse response =
                approvalService.decide(authentication, request.getId(), new ApprovalDecisionRequest(null), true);

        assertEquals(ApprovalStatus.APPROVED, response.status());
        verify(adminService).completeClientApproval(any(UUID.class));
        verify(auditLogService).record(eq("VENDOR_REGISTRATION"), eq(AuditOutcome.APPROVED),
                anyString(), anyString(), anyString(), eq(AUTHORIZER_KC), anyString(), anyString(),
                eq("AUTHORIZER"), any());
    }

    @Test
    void rejectionOfStaffCreationDisablesTheStagedAccount() {
        User actor = actor(AUTHORIZER_KC, AdminRole.AUTHORIZER);
        when(adminGuard.requireGovernanceAdmin(authentication)).thenReturn(actor);

        ApprovalRequest request = pending(ApprovalActionType.ADMIN_USER_CREATE, INITIATOR_KC);
        request.setPayload("{\"userId\":\"" + request.getTargetId() + "\"}");
        when(approvalRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        ApprovalRequestResponse response = approvalService.decide(
                authentication, request.getId(), new ApprovalDecisionRequest("wrong department"), false);

        assertEquals(ApprovalStatus.REJECTED, response.status());
        verify(adminService).completeBankUserRejection(any(UUID.class));
        verify(adminService, never()).completeBankUserApproval(any(UUID.class));
        verify(auditLogService).record(eq("ADMIN_USER_CREATE"), eq(AuditOutcome.REJECTED),
                anyString(), anyString(), anyString(), eq(AUTHORIZER_KC), anyString(), anyString(),
                eq("AUTHORIZER"), eq("wrong department"));
    }

    @Test
    void suspendExecutesTheVendorSuspensionOnApproval() {
        User actor = actor(AUTHORIZER_KC, AdminRole.AUTHORIZER);
        when(adminGuard.requireGovernanceAdmin(authentication)).thenReturn(actor);

        UUID clientId = UUID.randomUUID();
        ApprovalRequest request = pending(ApprovalActionType.CLIENT_SUSPEND, INITIATOR_KC);
        request.setPayload("{\"clientId\":\"" + clientId + "\"}");
        when(approvalRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        approvalService.decide(authentication, request.getId(),
                new ApprovalDecisionRequest("fraud investigation"), true);

        verify(adminService).suspendClient(clientId);
    }

    // === State guards ===

    @Test
    void alreadyDecidedRequestCannotBeDecidedAgain() {
        User actor = actor(AUTHORIZER_KC, AdminRole.AUTHORIZER);
        when(adminGuard.requireGovernanceAdmin(authentication)).thenReturn(actor);

        ApprovalRequest request = pending(ApprovalActionType.SERVICE_ASSIGNMENT, INITIATOR_KC);
        request.approve("someone-else", "Someone Else", "AUTHORIZER", "done earlier");
        when(approvalRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        ApiException error = assertThrows(ApiException.class, () -> approvalService.decide(
                authentication, request.getId(), new ApprovalDecisionRequest("second look"), true));

        assertEquals("ALREADY_DECIDED", error.getErrorCode());
        assertEquals(409, error.getStatus().value());
    }

    // === Queue visibility ===

    @Test
    void initiatorSeesOnlyTheirOwnRequests() {
        User actor = actor(INITIATOR_KC, AdminRole.INITIATOR);
        when(adminGuard.requireGovernanceAdmin(authentication)).thenReturn(actor);

        ApprovalRequest mine = pending(ApprovalActionType.VENDOR_REGISTRATION, INITIATOR_KC);
        ApprovalRequest theirs = pending(ApprovalActionType.SERVICE_ASSIGNMENT, "someone-else");
        when(approvalRequestRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(mine, theirs));

        List<ApprovalRequestResponse> visible = approvalService.listRequests(authentication, "ALL");

        assertEquals(1, visible.size());
        assertEquals(mine.getId(), visible.get(0).id());
    }

    @Test
    void authorizerSeesTheWholeQueue() {
        User actor = actor(AUTHORIZER_KC, AdminRole.AUTHORIZER);
        when(adminGuard.requireGovernanceAdmin(authentication)).thenReturn(actor);

        ApprovalRequest mine = pending(ApprovalActionType.VENDOR_REGISTRATION, "someone-else");
        ApprovalRequest theirs = pending(ApprovalActionType.SERVICE_ASSIGNMENT, "another-person");
        when(approvalRequestRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(mine, theirs));

        assertEquals(2, approvalService.listRequests(authentication, "ALL").size());
    }

    // === Helpers ===

    private User actor(String keycloakUserId, AdminRole adminRole) {
        return User.builder()
                .id(UUID.randomUUID())
                .keycloakUserId(keycloakUserId)
                .username(keycloakUserId + "@skybank.sl")
                .email(keycloakUserId + "@skybank.sl")
                .firstName("Test")
                .lastName("Admin")
                .role(Role.ROLE_ADMIN)
                .adminRole(adminRole)
                .enabled(true)
                .build();
    }

    private ApprovalRequest pending(ApprovalActionType type, String initiatedBy) {
        User initiator = User.builder()
                .keycloakUserId(initiatedBy)
                .email(initiatedBy + "@skybank.sl")
                .firstName("Initiator")
                .lastName("Person")
                .build();
        return ApprovalRequest.builder()
                .id(UUID.randomUUID())
                .actionType(type)
                .payload("{}")
                .targetLabel("Target " + type)
                .targetId(UUID.randomUUID().toString())
                .status(ApprovalStatus.PENDING)
                .initiatedBy(initiatedBy)
                .initiatedByName("Initiator Person")
                .initiatedByEmail(initiator.getEmail())
                .initiatedByRole("INITIATOR")
                .initiatedComment(type.isCommentRequired() ? "because reasons" : null)
                .build();
    }
}
