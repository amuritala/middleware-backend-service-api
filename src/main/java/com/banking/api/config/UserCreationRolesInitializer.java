package com.banking.api.config;

import com.banking.api.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Makes sure the workflow roles exist on realms imported before they were introduced. */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserCreationRolesInitializer {

    private final AdminService adminService;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureRoles() {
        try {
            adminService.ensureUserCreationRoles();
        } catch (Exception e) {
            log.warn("Could not verify user-creation roles in Keycloak: {}", e.getMessage());
        }
    }
}
