package com.banking.api.service;

import com.banking.api.dto.RoleChangeApplicationRequest;
import com.banking.api.dto.RoleChangeApplicationResponse;
import com.banking.api.entity.RoleChangeApplication;
import com.banking.api.entity.RoleChangeApplicationStatus;
import com.banking.api.entity.User;
import com.banking.api.repository.RoleChangeApplicationRepository;
import com.banking.api.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerManagementService {

    private static final String CLIENT_ROLE = "ROLE_CLIENT";
    private static final Set<String> SYSTEM_ROLES = Set.of(
            "ROLE_ADMIN", CLIENT_ROLE, "default-roles-banking", "uma_authorization", "offline_access"
    );

    private final RoleChangeApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final Keycloak keycloakAdmin;

    @Value("${keycloak.realm}")
    private String realm;

    @Transactional
    public RoleChangeApplicationResponse submitRoleChange(
            String keycloakUserId,
            RoleChangeApplicationRequest request
    ) {
        User user = findUser(keycloakUserId);
        Set<String> requestedRoles = normalizeAndValidateRoles(request.requestedRoles());

        if (applicationRepository.existsByUserIdAndStatus(user.getId(), RoleChangeApplicationStatus.PENDING)) {
            throw new IllegalStateException("A role change application is already pending");
        }

        // Snapshot what the vendor genuinely does not hold yet - the part an
        // administrator actually reviews. The full target set stays in requestedRoles
        // so approval keeps replacing the whole role set.
        Set<String> currentlyHeld = keycloakAdmin.realm(realm).users().get(user.getKeycloakUserId())
                .roles().realmLevel().listAll().stream()
                .map(RoleRepresentation::getName)
                .collect(Collectors.toSet());
        Set<String> newlyRequested = requestedRoles.stream()
                .filter(role -> !currentlyHeld.contains(role))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        RoleChangeApplication application = RoleChangeApplication.builder()
                .userId(user.getId())
                .keycloakUserId(user.getKeycloakUserId())
                .requestedRoles(requestedRoles)
                .newlyRequestedRoles(newlyRequested)
                .build();

        return toResponse(applicationRepository.save(application), user);
    }

    @Transactional(readOnly = true)
    public List<RoleChangeApplicationResponse> getMyApplications(String keycloakUserId) {
        User user = findUser(keycloakUserId);
        return applicationRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(application -> toResponse(application, user))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleChangeApplicationResponse> getPendingApplications() {
        return applicationRepository.findByStatusOrderByCreatedAtAsc(RoleChangeApplicationStatus.PENDING).stream()
                .map(application -> toResponse(application, findUser(application.getKeycloakUserId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleChangeApplicationResponse getApplication(UUID applicationId) {
        RoleChangeApplication application = findApplication(applicationId);
        return toResponse(application, findUser(application.getKeycloakUserId()));
    }

    @Transactional
    public RoleChangeApplicationResponse approveApplication(UUID applicationId, String reviewer) {
        return approveApplication(applicationId, reviewer, null);
    }

    /**
     * Approve an application, optionally granting only part of what was asked for. A null
     * (or absent) subset grants every requested role - the original all-or-nothing behaviour.
     * The subset must be non-empty and must not contain roles the vendor never requested;
     * anything left out is simply never granted.
     */
    @Transactional
    public RoleChangeApplicationResponse approveApplication(
            UUID applicationId,
            String reviewer,
            Set<String> approvedRoles
    ) {
        RoleChangeApplication application = findApplication(applicationId);
        ensurePending(application);

        Set<String> rolesToGrant = resolveApprovedRoles(application, approvedRoles);

        User user = findUser(application.getKeycloakUserId());
        replaceKeycloakRoles(application.getKeycloakUserId(), rolesToGrant);
        application.approve(reviewer);
        return toResponse(applicationRepository.save(application), user);
    }

    /** Narrow the requested role set to what the reviewer agreed to grant. */
    private Set<String> resolveApprovedRoles(
            RoleChangeApplication application,
            Set<String> approvedRoles
    ) {
        Set<String> requested = application.getRequestedRoles();
        if (approvedRoles == null) {
            return requested;
        }

        Set<String> normalized = approvedRoles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one service must be approved - reject the request instead");
        }

        Set<String> neverRequested = normalized.stream()
                .filter(role -> !requested.contains(role))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!neverRequested.isEmpty()) {
            throw new IllegalArgumentException(
                    "Only roles the vendor requested can be approved: " + neverRequested
            );
        }
        return normalized;
    }

    @Transactional
    public RoleChangeApplicationResponse rejectApplication(UUID applicationId, String reviewer) {
        RoleChangeApplication application = findApplication(applicationId);
        ensurePending(application);

        User user = findUser(application.getKeycloakUserId());
        application.reject(reviewer);
        return toResponse(applicationRepository.save(application), user);
    }

    private Set<String> normalizeAndValidateRoles(Set<String> requestedRoles) {
        if (requestedRoles == null || requestedRoles.isEmpty()) {
            throw new IllegalArgumentException("At least one service role is required");
        }

        Set<String> normalized = requestedRoles.stream()
                .map(role -> role == null ? "" : role.trim())
                .filter(role -> !role.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (normalized.stream().anyMatch(SYSTEM_ROLES::contains)) {
            throw new IllegalArgumentException("System roles cannot be requested");
        }

        Set<String> availableRoles = keycloakAdmin.realm(realm).roles().list().stream()
                .map(RoleRepresentation::getName)
                .filter(role -> !SYSTEM_ROLES.contains(role))
                .collect(Collectors.toSet());

        Set<String> invalidRoles = normalized.stream()
                .filter(role -> !availableRoles.contains(role))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!invalidRoles.isEmpty()) {
            throw new IllegalArgumentException("Unknown roles: " + invalidRoles);
        }
        return normalized;
    }

    private void replaceKeycloakRoles(String keycloakUserId, Set<String> requestedRoles) {
        RealmResource realmResource = keycloakAdmin.realm(realm);
        UserResource userResource = realmResource.users().get(keycloakUserId);

        List<RoleRepresentation> currentManagedRoles = userResource.roles().realmLevel().listAll().stream()
                .filter(role -> !SYSTEM_ROLES.contains(role.getName()))
                .toList();
        if (!currentManagedRoles.isEmpty()) {
            userResource.roles().realmLevel().remove(currentManagedRoles);
        }

        Set<String> rolesToAssign = new LinkedHashSet<>(requestedRoles);
        rolesToAssign.add(CLIENT_ROLE);
        List<RoleRepresentation> newRoles = rolesToAssign.stream()
                .map(role -> realmResource.roles().get(role).toRepresentation())
                .toList();
        userResource.roles().realmLevel().add(newRoles);
        log.info("Updated Keycloak roles for user {}: {}", keycloakUserId, rolesToAssign);
    }

    private User findUser(String keycloakUserId) {
        return userRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
    }

    private RoleChangeApplication findApplication(UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new EntityNotFoundException("Role change application not found"));
    }

    private void ensurePending(RoleChangeApplication application) {
        if (application.getStatus() != RoleChangeApplicationStatus.PENDING) {
            throw new IllegalStateException("Role change application is not pending");
        }
    }

    private RoleChangeApplicationResponse toResponse(RoleChangeApplication application, User user) {
        return new RoleChangeApplicationResponse(
                application.getId(),
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                application.getRequestedRoles(),
                application.getNewlyRequestedRoles(),
                application.getStatus(),
                application.getReviewedBy(),
                application.getReviewedAt(),
                application.getCreatedAt()
        );
    }
}
