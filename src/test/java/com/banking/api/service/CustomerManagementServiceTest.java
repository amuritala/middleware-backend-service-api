package com.banking.api.service;

import com.banking.api.dto.RoleChangeApplicationResponse;
import com.banking.api.entity.RoleChangeApplication;
import com.banking.api.entity.User;
import com.banking.api.repository.RoleChangeApplicationRepository;
import com.banking.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.mockito.Answers;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerManagementServiceTest {

    private final RoleChangeApplicationRepository applications = mock(RoleChangeApplicationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final Keycloak keycloak = mock(Keycloak.class, Answers.RETURNS_DEEP_STUBS);
    private final CustomerManagementService service =
            new CustomerManagementService(applications, users, keycloak);

    private RoleChangeApplication application() {
        RoleChangeApplication app = RoleChangeApplication.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .keycloakUserId("kc-1")
                .requestedRoles(new LinkedHashSet<>(Set.of("ROLE_A", "ROLE_B", "ROLE_OLD")))
                .newlyRequestedRoles(new LinkedHashSet<>(Set.of("ROLE_A", "ROLE_B")))
                .build();
        when(applications.findById(app.getId())).thenReturn(Optional.of(app));
        when(applications.save(any(RoleChangeApplication.class))).thenAnswer(i -> i.getArgument(0));
        when(users.findByKeycloakUserId("kc-1")).thenReturn(Optional.of(new User()));
        return app;
    }

    @Test
    void approvalDropsDeclinedNewRoles() {
        RoleChangeApplication app = application();

        RoleChangeApplicationResponse response =
                service.approveApplication(app.getId(), "admin", Set.of("ROLE_B"));

        assertEquals(Set.of("ROLE_A", "ROLE_OLD"), Set.copyOf(response.requestedRoles()));
        assertEquals(Set.of("ROLE_A"), Set.copyOf(response.newlyRequestedRoles()));
    }

    @Test
    void cannotDeclineRoleThatWasNotNewlyRequested() {
        RoleChangeApplication app = application();

        assertThrows(IllegalArgumentException.class,
                () -> service.approveApplication(app.getId(), "admin", Set.of("ROLE_OLD")));
    }
}
