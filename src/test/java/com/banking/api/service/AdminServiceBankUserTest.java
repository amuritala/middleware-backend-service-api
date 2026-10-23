package com.banking.api.service;

import com.banking.api.dto.AdminUserResponse;
import com.banking.api.dto.CreateBankUserRequest;
import com.banking.api.entity.Role;
import com.banking.api.entity.User;
import com.banking.api.exception.ApiException;
import com.banking.api.repository.ApiKeyRepository;
import com.banking.api.repository.ClientRepository;
import com.banking.api.repository.UserRepository;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminServiceBankUserTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final Keycloak keycloak = mock(Keycloak.class);
    private final WelcomeEmailService welcomeEmail = mock(WelcomeEmailService.class);
    private final AdminService service = new AdminService(
            mock(ClientRepository.class), mock(ApiKeyRepository.class), userRepository, keycloak, welcomeEmail);

    private final CreateBankUserRequest request = CreateBankUserRequest.builder()
            .email("jane@bank.com").firstName("Jane").lastName("Doe").password("Temp#1234").build();

    AdminServiceBankUserTest() {
        ReflectionTestUtils.setField(service, "realm", "banking");
    }

    @Test
    void createsRoleAdminUserWithoutClientAndForcesPasswordChange() {
        RealmResource realm = mock(RealmResource.class);
        UsersResource users = mock(UsersResource.class);
        UserResource userResource = mock(UserResource.class);
        RolesResource roles = mock(RolesResource.class);
        RoleResource roleResource = mock(RoleResource.class);
        RoleMappingResource mappings = mock(RoleMappingResource.class);
        RoleScopeResource realmLevel = mock(RoleScopeResource.class);

        when(userRepository.existsByUsername("jane@bank.com")).thenReturn(false);
        when(keycloak.realm("banking")).thenReturn(realm);
        when(realm.users()).thenReturn(users);
        when(realm.roles()).thenReturn(roles);
        when(roles.get("ROLE_ADMIN")).thenReturn(roleResource);
        RoleRepresentation adminRole = new RoleRepresentation();
        adminRole.setName("ROLE_ADMIN");
        when(roleResource.toRepresentation()).thenReturn(adminRole);
        Response created = Response.created(URI.create("http://kc/admin/realms/banking/users/kc-1")).build();
        when(users.create(any())).thenReturn(created);
        when(users.get("kc-1")).thenReturn(userResource);
        when(userResource.roles()).thenReturn(mappings);
        when(mappings.realmLevel()).thenReturn(realmLevel);
        when(realmLevel.listAll()).thenReturn(List.of(adminRole));
        UserRepresentation rep = new UserRepresentation();
        rep.setId("kc-1");
        rep.setUsername("jane@bank.com");
        rep.setEnabled(true);
        when(userResource.toRepresentation()).thenReturn(rep);

        AdminUserResponse response = service.createBankUser(request);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(Role.ROLE_ADMIN, saved.getValue().getRole());
        assertNull(saved.getValue().getClient());
        assertTrue(saved.getValue().getMustChangePassword());
        verify(realmLevel).add(List.of(adminRole));
        verify(welcomeEmail).sendTemporaryPassword("jane@bank.com", "Jane", "Temp#1234");
        assertEquals("BANK_USER", response.userType());
    }

    @Test
    void rejectsDuplicateEmail() {
        when(userRepository.existsByUsername("jane@bank.com")).thenReturn(true);

        assertThrows(ApiException.class, () -> service.createBankUser(request));
        verify(userRepository, never()).save(any());
    }
}
