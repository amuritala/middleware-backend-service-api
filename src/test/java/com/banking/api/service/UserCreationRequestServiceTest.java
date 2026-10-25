package com.banking.api.service;

import com.banking.api.dto.CreateBankUserRequest;
import com.banking.api.dto.UserCreationRequestResponse;
import com.banking.api.entity.UserCreationRequest;
import com.banking.api.entity.UserCreationRequestStatus;
import com.banking.api.exception.ApiException;
import com.banking.api.repository.UserCreationRequestRepository;
import com.banking.api.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserCreationRequestServiceTest {

    private final UserCreationRequestRepository repository = mock(UserCreationRequestRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AdminService adminService = mock(AdminService.class);
    private final UserCreationRequestService service =
            new UserCreationRequestService(repository, userRepository, adminService);

    private static final java.util.Set<String> BANK_AUTH = java.util.Set.of("ROLE_AUTHORISE_USER_CREATION");
    private static final java.util.Set<String> REG_AUTH = java.util.Set.of("ROLE_AUTHORISE_REGISTRATION");

    private final CreateBankUserRequest payload = CreateBankUserRequest.builder()
            .email("jane@bank.com").firstName("Jane").lastName("Doe").password("Temp#1234").build();

    private UserCreationRequest pending() {
        return UserCreationRequest.builder().id(UUID.randomUUID()).email("jane@bank.com")
                .firstName("Jane").lastName("Doe").temporaryPassword("Temp#1234")
                .requestedBy("maker@bank.com").build();
    }

    @Test
    void submitQueuesRequestWithoutCreatingUser() {
        when(adminService.validateBankUserRoles(any())).thenReturn(java.util.List.of());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        UserCreationRequestResponse response = service.submit(payload, "maker@bank.com");

        assertEquals(UserCreationRequestStatus.PENDING, response.status());
        verify(adminService, never()).createBankUser(any());
    }

    @Test
    void submitRejectsExistingUserAndDuplicatePending() {
        when(userRepository.existsByUsername("jane@bank.com")).thenReturn(true);
        assertThrows(ApiException.class, () -> service.submit(payload, "maker@bank.com"));

        when(userRepository.existsByUsername("jane@bank.com")).thenReturn(false);
        when(repository.existsByEmailIgnoreCaseAndStatus("jane@bank.com", UserCreationRequestStatus.PENDING))
                .thenReturn(true);
        assertThrows(ApiException.class, () -> service.submit(payload, "maker@bank.com"));
    }

    @Test
    void approveCreatesUserAndClearsPassword() {
        UserCreationRequest request = pending();
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        UserCreationRequestResponse response = service.approve(request.getId(), "checker@bank.com", BANK_AUTH);

        verify(adminService).createBankUser(any());
        assertEquals(UserCreationRequestStatus.APPROVED, response.status());
        assertEquals("checker@bank.com", response.reviewedBy());
        assertNull(request.getTemporaryPassword());
    }

    @Test
    void requesterCannotDecideOwnRequest() {
        UserCreationRequest request = pending();
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThrows(ApiException.class, () -> service.approve(request.getId(), "MAKER@bank.com", BANK_AUTH));
        assertThrows(ApiException.class, () -> service.reject(request.getId(), "maker@bank.com", BANK_AUTH));
        verify(adminService, never()).createBankUser(any());
    }

    @Test
    void rejectDoesNotCreateUserAndDecidedRequestsAreFinal() {
        UserCreationRequest request = pending();
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals(UserCreationRequestStatus.REJECTED,
                service.reject(request.getId(), "checker@bank.com", BANK_AUTH).status());
        verify(adminService, never()).createBankUser(any());
        assertThrows(IllegalStateException.class, () -> service.approve(request.getId(), "checker@bank.com", BANK_AUTH));
    }

    @Test
    void approvingClientRequestCreatesClientFromStoredPayload() {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        com.banking.api.dto.RegisterRequest client = com.banking.api.dto.RegisterRequest.builder()
                .contactEmail("ops@acme.com").contactFirstName("Ann").contactLastName("Lee")
                .companyName("Acme").password("Temp#1234").build();

        UserCreationRequestResponse queued = service.submitClient(client, "maker@bank.com");
        assertEquals(com.banking.api.entity.UserCreationKind.CLIENT, queued.kind());
        verify(adminService, never()).createClient(any());

        org.mockito.ArgumentCaptor<UserCreationRequest> saved =
                org.mockito.ArgumentCaptor.forClass(UserCreationRequest.class);
        verify(repository).save(saved.capture());
        UserCreationRequest request = saved.getValue();
        request.setId(UUID.randomUUID());
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThrows(ApiException.class, () -> service.approve(request.getId(), "checker@bank.com", BANK_AUTH));
        verify(adminService, never()).createClient(any());

        service.approve(request.getId(), "checker@bank.com", REG_AUTH);

        org.mockito.ArgumentCaptor<com.banking.api.dto.RegisterRequest> created =
                org.mockito.ArgumentCaptor.forClass(com.banking.api.dto.RegisterRequest.class);
        verify(adminService).createClient(created.capture());
        assertEquals("Acme", created.getValue().getCompanyName());
        assertEquals("Temp#1234", created.getValue().getPassword());
        assertNull(request.getClientPayload());
    }
}
