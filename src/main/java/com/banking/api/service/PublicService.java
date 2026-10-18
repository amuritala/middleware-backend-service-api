package com.banking.api.service;

import com.banking.api.dto.AvailabilityResponse;
import com.banking.api.dto.ClientResponse;
import com.banking.api.dto.RegisterRequest;
import com.banking.api.entity.Client;
import com.banking.api.entity.ClientStatus;
import com.banking.api.entity.Role;
import com.banking.api.entity.User;
import com.banking.api.exception.ApiException;
import com.banking.api.repository.ClientRepository;
import com.banking.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class PublicService {

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final Keycloak keycloakAdmin;

    @Value("${keycloak.realm}")
    private String realm;

    @Transactional
    public ClientResponse registerClient(RegisterRequest request) {
        log.info("Registering new client: {}", request.getCompanyName());

        // 1. Create user in Keycloak
        String keycloakUserId = createKeycloakUser(request);

        // 2. Create client in local database
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

        // 3. Create user in local database
        User user = User.builder()
                .keycloakUserId(keycloakUserId)
                .username(request.getContactEmail())
                .email(request.getContactEmail())
                .firstName(request.getContactFirstName())
                .lastName(request.getContactLastName())
                .role(Role.ROLE_CLIENT)
                .enabled(true)
                .client(savedClient)
                .build();

        userRepository.save(user);

        log.info("Client registered successfully with ID: {}", savedClient.getId());
        return toClientResponse(savedClient);
    }

    private String createKeycloakUser(RegisterRequest request) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(request.getContactEmail());
        user.setEmail(request.getContactEmail());
        user.setFirstName(request.getContactFirstName());
        user.setLastName(request.getContactLastName());
        user.setEnabled(true);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.getPassword());
        credential.setTemporary(false);
        user.setCredentials(Collections.singletonList(credential));

        // Assign default client role
        // This would need to be implemented based on your Keycloak setup

        try (var response = keycloakAdmin.realm(realm).users().create(user)) {
            if (response.getStatus() == 409) {
                // Keycloak already holds this email/username - a conflict, not a server fault.
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

    public AvailabilityResponse checkAvailability(String username, String email) {
        boolean usernameAvailable = !userRepository.existsByUsername(username);
        boolean emailAvailable = !userRepository.existsByEmail(email);

        return AvailabilityResponse.builder()
                .usernameAvailable(usernameAvailable)
                .emailAvailable(emailAvailable)
                .build();
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
                .status(client.getStatus())
                .industry(client.getIndustry())
                .createdAt(client.getCreatedAt())
                .build();
    }
}