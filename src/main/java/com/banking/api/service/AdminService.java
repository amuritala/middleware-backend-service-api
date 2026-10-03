package com.banking.api.service;

import com.banking.api.dto.ClientResponse;
import com.banking.api.entity.ApiKey;
import com.banking.api.entity.Client;
import com.banking.api.entity.ClientStatus;
import com.banking.api.entity.User;
import com.banking.api.repository.ApiKeyRepository;
import com.banking.api.repository.ClientRepository;
import com.banking.api.repository.UserRepository;
import com.banking.api.util.ApiKeyGenerator;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.keycloak.representations.idm.RoleRepresentation;
import com.banking.api.dto.AdminUserResponse;
import com.banking.api.dto.UpdateUserRequest;
import com.banking.api.dto.UserRolesRequest;
import jakarta.persistence.EntityNotFoundException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final ClientRepository clientRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final Keycloak keycloakAdmin;

    @Value("${keycloak.realm}")
    private String realm;

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

    @Transactional
    public ClientResponse approveClient(UUID clientId, List<String> approvedRoles) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new EntityNotFoundException("Client not found"));

        if (client.getStatus() != ClientStatus.PENDING) {
            throw new IllegalStateException("Client is not in pending state");
        }

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
                    roles
            );
    }

    // === Mappers ===

    private ClientResponse toClientResponse(Client client) {
        return ClientResponse.builder()
                .id(client.getId())
                .companyName(client.getCompanyName())
                .registrationNumber(client.getRegistrationNumber())
                .contactFirstName(client.getContactFirstName())
                .contactLastName(client.getContactLastName())
                .contactEmail(client.getEmail())
                .contactPhone(client.getPhone())
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
