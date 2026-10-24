package com.banking.api.service;

import com.banking.api.dto.AdminMeResponse;
import com.banking.api.dto.AdminUserSummaryResponse;
import com.banking.api.dto.ClientResponse;
import com.banking.api.dto.InitiateAdminUserRequest;
import com.banking.api.dto.RegisterRequest;
import com.banking.api.entity.AdminRole;
import com.banking.api.entity.ApiKey;
import com.banking.api.entity.Client;
import com.banking.api.entity.ClientStatus;
import com.banking.api.entity.Role;
import com.banking.api.entity.User;
import com.banking.api.entity.ApprovalStatus;
import com.banking.api.exception.ApiException;
import com.banking.api.repository.ApiKeyRepository;
import com.banking.api.repository.ApprovalRequestRepository;
import com.banking.api.repository.ClientRepository;
import com.banking.api.repository.UserRepository;
import com.banking.api.util.ApiKeyGenerator;
import com.banking.api.util.TemporaryPasswordGenerator;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.keycloak.representations.idm.RoleRepresentation;
import com.banking.api.dto.AdminUserResponse;
import com.banking.api.dto.UpdateUserRequest;
import com.banking.api.dto.UserRolesRequest;
import jakarta.persistence.EntityNotFoundException;

/**
 * Vendor and staff account operations. Administrator-created accounts no longer go live on
 * the spot: {@code provisionClientPending} / {@code provisionBankUserPending} build a disabled
 * account behind a maker-checker request, and the {@code complete*} methods only run once an
 * authorizer has signed the request off. The temporary password is generated at completion
 * time, emailed, and never returned through the API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final ClientRepository clientRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final Keycloak keycloakAdmin;
    private final WelcomeEmailService welcomeEmailService;

    @Value("${keycloak.realm}")
    private String realm;

    // === Vendor registration (maker-checker step 1: propose) ===

    /**
     * Stages a vendor account for an initiated registration. The Keycloak user exists but is
     * disabled and the local client sits in PENDING, so nothing can sign in and no service
     * will accept the vendor until an authorizer completes the request. The Keycloak user is
     * created here so a duplicate email is caught at initiation rather than at approval.
     */
    @Transactional
    public Client provisionClientPending(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getContactEmail())) {
            throw new ApiException(
                    "EMAIL_ALREADY_REGISTERED",
                    "A user with this email address already exists",
                    HttpStatus.CONFLICT
            );
        }

        String keycloakUserId = createKeycloakUser(request, false);

        Client client = Client.builder()
                .companyName(request.getCompanyName())
                .registrationNumber(request.getRegistrationNumber())
                .contactFirstName(request.getContactFirstName())
                .contactLastName(request.getContactLastName())
                .email(request.getContactEmail())
                .phone(request.getPhoneNumber())
                .contactPhone(request.getContactPhone())
                .country(request.getCountry())
                .address(request.getCompanyAddress())
                .taxId(request.getTaxId())
                .industry(request.getIndustry())
                .keycloakUserId(keycloakUserId)
                .status(ClientStatus.PENDING)
                .build();
        Client savedClient = clientRepository.save(client);

        userRepository.save(User.builder()
                .keycloakUserId(keycloakUserId)
                .username(request.getContactEmail())
                .email(request.getContactEmail())
                .firstName(request.getContactFirstName())
                .lastName(request.getContactLastName())
                .role(Role.ROLE_CLIENT)
                .enabled(false)
                .mustChangePassword(true)
                .client(savedClient)
                .build());

        log.info("Vendor {} staged as PENDING (Keycloak user disabled)", savedClient.getCompanyName());
        return savedClient;
    }

    /**
     * Maker-checker step 2: the authorizer approved, so the vendor goes live. A fresh
     * temporary password is generated here - never stored, only emailed - the Keycloak user
     * is enabled with ROLE_CLIENT, and the client moves PENDING -> APPROVED -> ACTIVE.
     */
    @Transactional
    public ClientResponse completeClientApproval(UUID clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new EntityNotFoundException("Client not found"));

        client.approve();
        client.activate();
        Client savedClient = clientRepository.save(client);

        boolean emailSent = false;
        if (client.getKeycloakUserId() != null) {
            String temporaryPassword = TemporaryPasswordGenerator.generate();
            resetKeycloakPassword(client.getKeycloakUserId(), temporaryPassword);
            enableKeycloakUser(client.getKeycloakUserId(), List.of("ROLE_CLIENT"));
            userRepository.findByKeycloakUserId(client.getKeycloakUserId()).ifPresent(user -> {
                user.setEnabled(true);
                user.setMustChangePassword(true);
                userRepository.save(user);
            });
            emailSent = welcomeEmailService.sendTemporaryPassword(
                    client.getEmail(), client.getContactFirstName(), temporaryPassword);
        }

        log.info("Client {} approved and activated (temporary password emailed: {})",
                savedClient.getCompanyName(), emailSent);

        ClientResponse response = toClientResponse(savedClient);
        response.setWelcomeEmailSent(emailSent);
        return response;
    }

    /** The authorizer refused the registration: the vendor stays disabled and is marked rejected. */
    @Transactional
    public Client completeClientRejection(UUID clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new EntityNotFoundException("Client not found"));
        client.reject();
        Client savedClient = clientRepository.save(client);
        // The Keycloak user was never enabled; it stays that way.
        log.info("Client {} rejected at the authorization step", savedClient.getCompanyName());
        return savedClient;
    }

    // === Staff accounts (maker-checker step 1: propose) ===

    /**
     * Stages a bank staff administrator. The account carries its governance role from the
     * start but stays disabled until an authorizer approves; the temporary password is
     * generated and emailed at that point.
     */
    @Transactional
    public User provisionBankUserPending(InitiateAdminUserRequest request) {
        if (userRepository.existsByUsername(request.getEmail())
                || userRepository.existsByEmail(request.getEmail())) {
            throw new ApiException(
                    "EMAIL_ALREADY_REGISTERED",
                    "A user with this email address already exists",
                    HttpStatus.CONFLICT
            );
        }

        RegisterRequest credentials = RegisterRequest.builder()
                .contactEmail(request.getEmail())
                .contactFirstName(request.getFirstName())
                .contactLastName(request.getLastName())
                .build();
        String keycloakUserId = createKeycloakUser(credentials, false);

        User user = userRepository.save(User.builder()
                .keycloakUserId(keycloakUserId)
                .username(request.getEmail())
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(Role.ROLE_ADMIN)
                .adminRole(request.getAdminRole())
                .enabled(false)
                .mustChangePassword(true)
                .build());

        log.info("Bank user {} staged as PENDING with {} (Keycloak user disabled)",
                request.getEmail(), request.getAdminRole());
        return user;
    }

    /**
     * Maker-checker step 2 for staff: enable the account, hand it a freshly generated
     * temporary password by email, and flag it so the first sign-in forces a change.
     */
    @Transactional
    public AdminUserSummaryResponse completeBankUserApproval(UUID localUserId) {
        User user = userRepository.findById(localUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        String temporaryPassword = TemporaryPasswordGenerator.generate();
        resetKeycloakPassword(user.getKeycloakUserId(), temporaryPassword);
        enableKeycloakUser(user.getKeycloakUserId(), List.of("ROLE_ADMIN"));

        user.setEnabled(true);
        user.setMustChangePassword(true);
        User saved = userRepository.save(user);

        boolean emailSent = welcomeEmailService.sendTemporaryPassword(
                saved.getEmail(), saved.getFirstName(), temporaryPassword);
        log.info("Bank user {} approved and activated (temporary password emailed: {})",
                saved.getEmail(), emailSent);

        return toStaffSummary(saved);
    }

    /** The authorizer refused the staff account; it stays disabled and unusable. */
    @Transactional
    public void completeBankUserRejection(UUID localUserId) {
        userRepository.findById(localUserId).ifPresent(user -> {
            user.setEnabled(false);
            userRepository.save(user);
            if (user.getKeycloakUserId() != null) {
                disableKeycloakUser(user.getKeycloakUserId());
            }
            log.info("Bank user {} rejected at the authorization step", user.getEmail());
        });
    }

    // === Service-role assignment (executed on approval) ===

    /** Grants or revokes service roles on a user once an authorizer approved the request. */
    @Transactional
    public void applyServiceRoles(String keycloakUserId, List<String> roles, String mode) {
        UserResource userResource = keycloakUser(keycloakUserId);
        List<RoleRepresentation> resolved = resolveServiceRoles(roles);
        if ("REVOKE".equalsIgnoreCase(mode)) {
            userResource.roles().realmLevel().remove(resolved);
        } else {
            userResource.roles().realmLevel().add(resolved);
        }
        log.info("Service roles {} {} for user {}", roles, mode, keycloakUserId);
    }

    // === Governance role assignment (executed on approval) ===

    /** Sets the governance role of a staff account once an authorizer approved the request. */
    @Transactional
    public AdminUserSummaryResponse applyAdminRole(UUID localUserId, AdminRole adminRole) {
        User user = userRepository.findById(localUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        user.setAdminRole(adminRole);
        User saved = userRepository.save(user);
        log.info("Governance role of {} set to {}", saved.getEmail(), adminRole);
        return toStaffSummary(saved);
    }

    // === Staff directory / governance profile ===

    /** Every bank staff administrator, with the governance role that decides their access. */
    public List<AdminUserSummaryResponse> getStaffUsers() {
        return userRepository.findStaffByRole(Role.ROLE_ADMIN).stream()
                .map(this::toStaffSummary)
                .collect(Collectors.toList());
    }

    /** The signed-in administrator as the governance layer sees them. */
    public AdminMeResponse getMe(String keycloakUserId) {
        User user = userRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new ApiException(
                        "GOVERNANCE_ROLE_REQUIRED",
                        "No local account exists for this session",
                        HttpStatus.FORBIDDEN
                ));
        String fullName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return new AdminMeResponse(
                user.getKeycloakUserId(),
                user.getFirstName(),
                user.getLastName(),
                fullName,
                user.getEmail(),
                user.getAdminRole(),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                user.getAdminRole() != null ? user.getAdminRole().getPermissions() : List.of()
        );
    }

    // === Client Management ===

    public List<ClientResponse> getAllClients() {
        return clientRepository.findAll().stream()
                .map(this::toClientResponse)
                .collect(Collectors.toList());
    }

    public List<ClientResponse> getClientsByStatus(ClientStatus status) {
        return clientRepository.findByStatus(status).stream()
                .map(this::toClientResponse)
                .collect(Collectors.toList());
    }

    public ClientResponse getClientById(UUID id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Client not found"));
        return toClientResponse(client);
    }

    /**
     * Reviews a self-registered vendor - the queue a vendor enters by signing up themselves,
     * which is separate from the maker-checker flow used for administrator-initiated
     * registrations.
     */
    @Transactional
    public ClientResponse approveClient(UUID clientId, List<String> approvedRoles) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new EntityNotFoundException("Client not found"));

        if (client.getStatus() != ClientStatus.PENDING) {
            throw new IllegalStateException("Client is not in pending state");
        }
        refuseWhenQueuedForAuthorization(clientId);

        approvedRoles.add("ROLE_CLIENT");

        client.approve();
        Client savedClient = clientRepository.save(client);

        // Enable Keycloak user if needed
        if (client.getKeycloakUserId() != null) {
            enableKeycloakUser(client.getKeycloakUserId(), approvedRoles);
        }

        log.info("Client approved: {}", client.getCompanyName());
        return toClientResponse(savedClient);
    }

    @Transactional
    public ClientResponse rejectClient(UUID clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new EntityNotFoundException("Client not found"));

        if (client.getStatus() != ClientStatus.PENDING) {
            throw new IllegalStateException("Client is not in pending state");
        }
        refuseWhenQueuedForAuthorization(clientId);

        client.reject();
        Client savedClient = clientRepository.save(client);

        // Disable Keycloak user
        if (client.getKeycloakUserId() != null) {
            disableKeycloakUser(client.getKeycloakUserId());
        }

        log.info("Client rejected: {}", client.getCompanyName());
        return toClientResponse(savedClient);
    }

    @Transactional
    public ClientResponse suspendClient(UUID clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new EntityNotFoundException("Client not found"));

        client.suspend();
        Client savedClient = clientRepository.save(client);

        // Disable all API keys
        apiKeyRepository.findByClient(client).forEach(apiKey -> {
            apiKey.revoke();
            apiKeyRepository.save(apiKey);
        });

        // Disable Keycloak user
        if (client.getKeycloakUserId() != null) {
            disableKeycloakUser(client.getKeycloakUserId());
        }

        log.info("Client suspended: {}", client.getCompanyName());
        return toClientResponse(savedClient);
    }

    @Transactional
    public ClientResponse activateClient(UUID clientId, List<String> approvedRoles) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new EntityNotFoundException("Client not found"));

        client.activate();
        Client savedClient = clientRepository.save(client);

        approvedRoles.add("ROLE_CLIENT");

        // Enable Keycloak user
        if (client.getKeycloakUserId() != null) {
            enableKeycloakUser(client.getKeycloakUserId(), approvedRoles);
        }

        log.info("Client activated: {}", client.getCompanyName());
        return toClientResponse(savedClient);
    }

    /**
     * An administrator-initiated registration also lives in the approvals queue, so its
     * decision has to be made there - approving through the self-registration shortcut
     * would leave the queued request pending forever and skip the audit trail.
     */
    private void refuseWhenQueuedForAuthorization(UUID clientId) {
        boolean queued = approvalRequestRepository
                .findByStatusOrderByCreatedAtDesc(ApprovalStatus.PENDING)
                .stream()
                .anyMatch(request -> clientId.toString().equals(request.getTargetId()));
        if (queued) {
            throw new ApiException(
                    "APPROVAL_QUEUE",
                    "This registration is awaiting authorization - decide it from the approvals queue",
                    HttpStatus.CONFLICT
            );
        }
    }

    // === API Key Management ===
    @Transactional
    public void revokeApiKey(UUID keyId) {
        ApiKey apiKey = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new EntityNotFoundException("API Key not found"));

        apiKey.revoke();
        apiKeyRepository.save(apiKey);
        log.info("API Key revoked: {}", keyId);
    }

    public List<String> getKeycloakRoles() {
        Set<String> excludedRoles = Set.of(
                "ROLE_ADMIN",
                "default-roles-banking",
                "uma_authorization",
                "offline_access",
                "ROLE_CLIENT"
        );

        return keycloakAdmin
                .realm(realm)
                .roles()
                .list()
                .stream()
                .map(RoleRepresentation::getName)
                .filter(role -> !excludedRoles.contains(role))
                .toList();
    }

    // === User Management ===

    public List<User> getClientUsers(UUID clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new EntityNotFoundException("Client not found"));
        return client.getUsers();
    }

    public List<AdminUserResponse> getKeycloakUsers(String search) {
        List<UserRepresentation> users = search == null || search.isBlank()
                ? keycloakAdmin.realm(realm).users().list()
                : keycloakAdmin.realm(realm).users().search(search);
        return users.stream().map(this::toAdminUserResponse).toList();
    }

    public AdminUserResponse getKeycloakUser(String userId) {
        return toAdminUserResponse(keycloakUser(userId).toRepresentation());
    }

    /** Same lookup, but a missing user is a null rather than an exception. */
    public AdminUserResponse getKeycloakUserOrNull(String userId) {
        try {
            return toAdminUserResponse(keycloakUser(userId).toRepresentation());
        } catch (Exception exception) {
            return null;
        }
    }

    @Transactional
    public AdminUserResponse updateKeycloakUser(String userId, UpdateUserRequest request) {
        UserResource userResource = keycloakUser(userId);
        UserRepresentation user = userResource.toRepresentation();

        if (request.username() != null) user.setUsername(request.username());
        if (request.email() != null) user.setEmail(request.email());
        if (request.firstName() != null) user.setFirstName(request.firstName());
        if (request.lastName() != null) user.setLastName(request.lastName());
        if (request.enabled() != null) user.setEnabled(request.enabled());
        if (request.emailVerified() != null) user.setEmailVerified(request.emailVerified());

        userResource.update(user);
        syncLocalUser(user);
        return toAdminUserResponse(userResource.toRepresentation());
    }

    public AdminUserResponse assignRoles(String userId, UserRolesRequest request) {
        UserResource userResource = keycloakUser(userId);
        List<RoleRepresentation> roles = resolveServiceRoles(request.roles());
        userResource.roles().realmLevel().add(roles);
        return toAdminUserResponse(userResource.toRepresentation());
    }

    public AdminUserResponse unassignRoles(String userId, UserRolesRequest request) {
        UserResource userResource = keycloakUser(userId);
        List<RoleRepresentation> roles = resolveServiceRoles(request.roles());
        userResource.roles().realmLevel().remove(roles);
        return toAdminUserResponse(userResource.toRepresentation());
    }

    // === Keycloak Helpers ===

    private String createKeycloakUser(RegisterRequest request, boolean enabled) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(request.getContactEmail());
        user.setEmail(request.getContactEmail());
        user.setFirstName(request.getContactFirstName());
        user.setLastName(request.getContactLastName());
        user.setEnabled(enabled);
        // An administrator supplied these details, so the address counts as verified.
        user.setEmailVerified(true);

        // The real credential only exists once the account is activated - it is generated
        // and emailed at approval time. This placeholder never leaves the server.
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(TemporaryPasswordGenerator.generate());
        credential.setTemporary(false);
        user.setCredentials(List.of(credential));

        try (var response = keycloakAdmin.realm(realm).users().create(user)) {
            if (response.getStatus() == 409) {
                throw new ApiException(
                        "EMAIL_ALREADY_REGISTERED",
                        "A user with this email address already exists",
                        HttpStatus.CONFLICT
                );
            }
            if (response.getStatus() != 201) {
                throw new RuntimeException("Failed to create Keycloak user");
            }
            return response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
        }
    }

    /** Overwrites the credential with a freshly generated temporary password. */
    private void resetKeycloakPassword(String keycloakUserId, String password) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        keycloakAdmin.realm(realm).users().get(keycloakUserId).resetPassword(credential);
    }

    private void assignRealmRole(String keycloakUserId, String roleName) {
        try {
            RealmResource realmResource = keycloakAdmin.realm(realm);
            RoleRepresentation role = realmResource.roles().get(roleName).toRepresentation();
            realmResource.users().get(keycloakUserId).roles().realmLevel().add(List.of(role));
        } catch (Exception e) {
            // The account is already usable; an admin can grant roles again from the Users screen.
            log.error("Failed to assign {} to {}", roleName, keycloakUserId, e);
        }
    }

    private void enableKeycloakUser( String userId, List<String> approvedRoles ) {
        try { RealmResource realmResource = keycloakAdmin.realm(realm);
        UserResource userResource = realmResource.users().get(userId); // Enable the user
        UserRepresentation user = userResource.toRepresentation();
        user.setEnabled(true); userResource.update(user);
        // Convert approved role names to Keycloak RoleRepresentations
         List<RoleRepresentation> rolesToAssign = approvedRoles.stream()
                 .map(roleName -> realmResource .roles() .get(roleName) .toRepresentation())
                 .toList(); // Assign all approved realm roles
             if (!rolesToAssign.isEmpty()) { userResource .roles() .realmLevel() .add(rolesToAssign); }
             log.info( "Keycloak user {} enabled and roles assigned: {}", userId, approvedRoles );
        } catch (Exception e) {
            log.error( "Failed to enable user {} and assign roles {}", userId, approvedRoles, e );
        }
    }

    private void disableKeycloakUser(String userId) {
        try {
            RealmResource realmResource = keycloakAdmin.realm(realm);
            UserResource userResource = realmResource.users().get(userId);
            UserRepresentation user = userResource.toRepresentation();
            user.setEnabled(false);
            userResource.update(user);
        } catch (Exception e) {
            log.error("Failed to disable Keycloak user: {}", e.getMessage());
        }
    }

    private void enableKeycloakUser(String userId) {
        try {
            RealmResource realmResource = keycloakAdmin.realm(realm);
            UserResource userResource = realmResource.users().get(userId);
            UserRepresentation user = userResource.toRepresentation();
            user.setEnabled(true);
            userResource.update(user);
        } catch (Exception e) {
            log.error("Failed to enable Keycloak user: {}", e.getMessage());
        }
    }

    // === Suspend / reinstate / time-lock (executed on approval) ===

    /**
     * Suspension blocks sign-in outright: the Keycloak user is disabled, so the password
     * grant itself fails, and the local flag makes every already-issued token refuse at the
     * API. Only an approved reinstatement reverses it.
     */
    @Transactional
    public void suspendUser(String keycloakUserId) {
        User user = requireLocalUser(keycloakUserId);
        user.setSuspended(true);
        // Suspension is indefinite - any pending time lock is superseded by it.
        user.setLockedUntil(null);
        userRepository.save(user);
        disableKeycloakUser(user.getKeycloakUserId());
        log.info("User {} suspended", user.getEmail());
    }

    /** Lifts a suspension. A lock still in force keeps the Keycloak account disabled. */
    @Transactional
    public void unsuspendUser(String keycloakUserId) {
        User user = requireLocalUser(keycloakUserId);
        user.setSuspended(false);
        boolean locked = user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(LocalDateTime.now());
        if (!locked) {
            user.setLockedUntil(null);
            if (Boolean.TRUE.equals(user.getEnabled())) {
                enableKeycloakUser(user.getKeycloakUserId());
            }
        }
        userRepository.save(user);
        log.info("User {} reinstated{}", user.getEmail(), locked ? " (time lock still in force)" : "");
    }

    /**
     * A time lock disables the Keycloak user until the requested minutes pass; the scheduled
     * release job clears it again. Access is refused from the moment of approval, both at
     * login (Keycloak) and at the API (the access-state filter).
     */
    @Transactional
    public void lockUser(String keycloakUserId, int minutes) {
        User user = requireLocalUser(keycloakUserId);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(minutes));
        userRepository.save(user);
        disableKeycloakUser(user.getKeycloakUserId());
        log.info("User {} locked for {} minutes", user.getEmail(), minutes);
    }

    /**
     * Releases every lock whose expiry has passed and re-enables those accounts in Keycloak.
     * Runs on a fixed schedule and on demand, so a lock never outlives its window even if the
     * application restarted while it was in force. Suspended accounts stay disabled - their
     * block is indefinite and only an approved reinstatement lifts it.
     */
    @Transactional
    public int releaseExpiredLocks() {
        List<User> expired = userRepository.findExpiredLocks(LocalDateTime.now());
        for (User user : expired) {
            user.setLockedUntil(null);
            if (!Boolean.TRUE.equals(user.getSuspended())
                    && Boolean.TRUE.equals(user.getEnabled())) {
                enableKeycloakUser(user.getKeycloakUserId());
            }
            userRepository.save(user);
            log.info("Time lock expired for {}", user.getEmail());
        }
        return expired.size();
    }

    private User requireLocalUser(String keycloakUserId) {
        return userRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new ApiException(
                        "TARGET_NOT_FOUND", "No user exists with that id", HttpStatus.NOT_FOUND));
    }

    private UserResource keycloakUser(String userId) {
            try {
                return keycloakAdmin.realm(realm).users().get(userId);
            } catch (Exception exception) {
                throw new EntityNotFoundException("Keycloak user not found: " + userId);
            }
        }

    private List<RoleRepresentation> resolveServiceRoles(List<String> roleNames) {
            Set<String> protectedRoles = Set.of(
                    "ROLE_ADMIN", "ROLE_CLIENT", "default-roles-banking", "uma_authorization", "offline_access"
            );
            Set<String> availableRoles = keycloakAdmin.realm(realm).roles().list().stream()
                    .map(RoleRepresentation::getName)
                    .collect(Collectors.toSet());
            Set<String> requestedRoles = roleNames.stream()
                    .map(role -> role == null ? "" : role.trim())
                    .filter(role -> !role.isBlank())
                    .collect(Collectors.toSet());

            if (requestedRoles.isEmpty() || requestedRoles.stream().anyMatch(protectedRoles::contains)) {
                throw new IllegalArgumentException("Only service roles may be assigned or unassigned");
            }
            Set<String> invalidRoles = requestedRoles.stream()
                    .filter(role -> !availableRoles.contains(role))
                    .collect(Collectors.toSet());
            if (!invalidRoles.isEmpty()) {
                throw new IllegalArgumentException("Unknown roles: " + invalidRoles);
            }
            return requestedRoles.stream()
                    .map(role -> keycloakAdmin.realm(realm).roles().get(role).toRepresentation())
                    .toList();
        }

    private void syncLocalUser(UserRepresentation keycloakUser) {
            userRepository.findByKeycloakUserId(keycloakUser.getId()).ifPresent(user -> {
                user.setUsername(keycloakUser.getUsername());
                user.setEmail(keycloakUser.getEmail());
                user.setFirstName(keycloakUser.getFirstName());
                user.setLastName(keycloakUser.getLastName());
                user.setEnabled(Boolean.TRUE.equals(keycloakUser.isEnabled()));
                userRepository.save(user);
            });
        }

    private AdminUserResponse toAdminUserResponse(UserRepresentation user) {
            List<String> roles = keycloakAdmin.realm(realm).users().get(user.getId())
                    .roles().realmLevel().listAll().stream()
                    .map(RoleRepresentation::getName)
                    .sorted()
                    .toList();
            return new AdminUserResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    Boolean.TRUE.equals(user.isEnabled()),
                    Boolean.TRUE.equals(user.isEmailVerified()),
                    roles,
                    roles.contains("ROLE_ADMIN") ? "BANK_USER" : "CLIENT"
            );
        }

    // === Mappers ===

    private AdminUserSummaryResponse toStaffSummary(User user) {
        String fullName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return new AdminUserSummaryResponse(
                user.getKeycloakUserId(),
                user.getFirstName(),
                user.getLastName(),
                fullName,
                user.getEmail(),
                user.getAdminRole(),
                Boolean.TRUE.equals(user.getEnabled()),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                Boolean.TRUE.equals(user.getSuspended()),
                user.getLockedUntil(),
                user.getLastLogin(),
                user.getCreatedAt()
        );
    }

    private ClientResponse toClientResponse(Client client) {
        return ClientResponse.builder()
                .id(client.getId())
                .companyName(client.getCompanyName())
                .registrationNumber(client.getRegistrationNumber())
                .contactFirstName(client.getContactFirstName())
                .contactLastName(client.getContactLastName())
                .contactEmail(client.getEmail())
                .contactPhone(client.getContactPhone())
                .phoneNumber(client.getPhone())
                .country(client.getCountry())
                .address(client.getAddress())
                .taxId(client.getTaxId())
                .industry(client.getIndustry())
                .status(client.getStatus())
                .createdAt(client.getCreatedAt())
                .approvedAt(client.getApprovedAt())
                .userCount(client.getUsers() != null ? client.getUsers().size() : 0)
                .build();
    }
}
