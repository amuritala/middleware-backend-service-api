package com.banking.api.config;

import com.banking.api.entity.Client;
import com.banking.api.entity.ClientStatus;
import com.banking.api.entity.Role;
import com.banking.api.entity.User;
import com.banking.api.repository.ClientRepository;
import com.banking.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Initializing data...");

        // Create admin user if it doesn't exist
        if (!userRepository.existsByUsername("admin")) {
            createAdminUser();
        }

        // Create sample client if none exist
        if (clientRepository.count() == 0) {
            createSampleClient();
        }

        log.info("Data initialization complete");
    }

    private void createAdminUser() {
        Client adminClient = Client.builder()
                .companyName("Bank Admin")
                .email("admin@bank.com")
                .status(ClientStatus.ACTIVE)
                .keycloakUserId("admin-keycloak-id")
                .build();

        Client savedClient = clientRepository.save(adminClient);

        User adminUser = User.builder()
                .keycloakUserId("admin-keycloak-id")
                .username("admin")
                .email("admin@bank.com")
                .firstName("System")
                .lastName("Administrator")
                .role(Role.ROLE_ADMIN)
                .enabled(true)
                .client(savedClient)
                .build();

        userRepository.save(adminUser);
        log.info("Admin user created");
    }

    private void createSampleClient() {
        Client client = Client.builder()
                .companyName("Sample Company")
                .registrationNumber("REG-001")
                .contactFirstName("John")
                .contactLastName("Doe")
                .email("sample@company.com")
                .phone("+1234567890")
                .country("USA")
                .address("123 Main St, New York, NY")
                .taxId("TAX-001")
                .status(ClientStatus.PENDING)
                .build();

        clientRepository.save(client);
        log.info("Sample client created with ID: {}", client.getId());
    }
}