package com.banking.api.service;

import com.banking.api.dto.CreateBankUserRequest;
import com.banking.api.dto.RegisterRequest;
import com.banking.api.dto.UserCreationRequestResponse;
import com.banking.api.entity.UserCreationKind;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.banking.api.entity.UserCreationRequest;
import com.banking.api.entity.UserCreationRequestStatus;
import com.banking.api.exception.ApiException;
import com.banking.api.repository.UserCreationRequestRepository;
import com.banking.api.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Two-step bank-user creation: one admin requests, a different admin authorises. */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserCreationRequestService {

    private final UserCreationRequestRepository repository;
    private final UserRepository userRepository;
    private final AdminService adminService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public UserCreationRequestResponse submit(CreateBankUserRequest request, String requester) {
        String email = request.getEmail().trim();
        ensureEmailFree(email);
        List<String> roles = adminService.validateBankUserRoles(request.getRoles());
        UserCreationRequest saved = repository.save(UserCreationRequest.builder()
                .kind(UserCreationKind.BANK_USER)
                .requestedRoles(String.join(",", roles))
                .email(email)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .temporaryPassword(request.getPassword())
                .requestedBy(requester)
                .build());
        log.info("Bank user creation requested for {} by {}", email, requester);
        return UserCreationRequestResponse.from(saved);
    }

    @Transactional
    public UserCreationRequestResponse submitClient(RegisterRequest request, String requester) {
        String email = request.getContactEmail().trim();
        ensureEmailFree(email);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not queue the client registration", e);
        }
        UserCreationRequest saved = repository.save(UserCreationRequest.builder()
                .kind(UserCreationKind.CLIENT)
                .companyName(request.getCompanyName())
                .email(email)
                .firstName(request.getContactFirstName())
                .lastName(request.getContactLastName())
                .clientPayload(payload)
                .requestedBy(requester)
                .build());
        log.info("Client registration requested for {} by {}", email, requester);
        return UserCreationRequestResponse.from(saved);
    }

    private void ensureEmailFree(String email) {
        if (userRepository.existsByUsername(email)) {
            throw new ApiException("EMAIL_ALREADY_REGISTERED",
                    "A user with this email address already exists", HttpStatus.CONFLICT);
        }
        if (repository.existsByEmailIgnoreCaseAndStatus(email, UserCreationRequestStatus.PENDING)) {
            throw new ApiException("REQUEST_ALREADY_PENDING",
                    "A creation request for this email is already awaiting approval", HttpStatus.CONFLICT);
        }
    }

    public List<UserCreationRequestResponse> list(UserCreationRequestStatus status) {
        List<UserCreationRequest> rows = status == null
                ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findByStatusOrderByCreatedAtAsc(status);
        return rows.stream().map(UserCreationRequestResponse::from).toList();
    }

    @Transactional
    public UserCreationRequestResponse approve(UUID id, String reviewer, Set<String> reviewerRoles) {
        UserCreationRequest request = pending(id);
        ensureMayDecide(request, reviewerRoles);
        if (request.getRequestedBy().equalsIgnoreCase(reviewer)) {
            throw new ApiException("SELF_APPROVAL_NOT_ALLOWED",
                    "You cannot decide a request you submitted", HttpStatus.FORBIDDEN);
        }
        if (request.getKind() == UserCreationKind.CLIENT) {
            try {
                adminService.createClient(objectMapper.readValue(request.getClientPayload(), RegisterRequest.class));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Stored client registration is unreadable", e);
            }
        } else {
            adminService.createBankUser(CreateBankUserRequest.builder()
                    .email(request.getEmail())
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .password(request.getTemporaryPassword())
                    .roles(request.getRequestedRoles() == null || request.getRequestedRoles().isBlank()
                            ? List.of() : List.of(request.getRequestedRoles().split(",")))
                    .build());
        }
        request.approve(reviewer);
        log.info("User creation request {} approved by {}", id, reviewer);
        return UserCreationRequestResponse.from(repository.save(request));
    }

    @Transactional
    public UserCreationRequestResponse reject(UUID id, String reviewer, Set<String> reviewerRoles) {
        UserCreationRequest request = pending(id);
        ensureMayDecide(request, reviewerRoles);
        if (request.getRequestedBy().equalsIgnoreCase(reviewer)) {
            throw new ApiException("SELF_APPROVAL_NOT_ALLOWED",
                    "You cannot decide a request you submitted", HttpStatus.FORBIDDEN);
        }
        request.reject(reviewer);
        log.info("User creation request {} rejected by {}", id, reviewer);
        return UserCreationRequestResponse.from(repository.save(request));
    }

    /** Client registrations need ROLE_AUTHORISE_REGISTRATION; bank users need ROLE_AUTHORISE_USER_CREATION. */
    private void ensureMayDecide(UserCreationRequest request, Set<String> reviewerRoles) {
        String required = request.getKind() == UserCreationKind.CLIENT
                ? "ROLE_AUTHORISE_REGISTRATION" : "ROLE_AUTHORISE_USER_CREATION";
        if (!reviewerRoles.contains(required)) {
            throw new ApiException("MISSING_AUTHORISER_ROLE",
                    "Deciding this request requires " + required, HttpStatus.FORBIDDEN);
        }
    }

    private UserCreationRequest pending(UUID id) {
        UserCreationRequest request = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User creation request not found"));
        if (request.getStatus() != UserCreationRequestStatus.PENDING) {
            throw new IllegalStateException("Request has already been decided");
        }
        return request;
    }
}
