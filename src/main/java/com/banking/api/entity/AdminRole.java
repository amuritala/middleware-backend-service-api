package com.banking.api.entity;

import java.util.List;

/**
 * Governance roles for bank staff administrators. Deliberately kept in the local database
 * rather than Keycloak: the frontend reads them through {@code GET /api/admin/me} and the
 * backend enforces every maker-checker rule against this column, so a role change takes
 * effect on the next request without waiting for a token refresh.
 *
 * <p>Coarse access to {@code /api/admin/**} is still gated by Keycloak's ROLE_ADMIN; these
 * roles decide *what* an administrator may do inside that area.</p>
 */
public enum AdminRole {

    /** Sees every screen, may initiate and authorize - but never its own requests. */
    SUPER_ADMIN("Super Admin",
            "Sees every screen and may both initiate and authorize actions, except ones they initiated themselves.",
            List.of(
                    "view.dashboard", "view.clients", "view.services", "view.role_requests",
                    "view.users", "view.approvals", "view.audit", "view.role_matrix",                    "initiate.vendor_registration", "initiate.service_assignment",
                    "initiate.client_suspend", "initiate.client_activate",
                    "initiate.admin_user_create", "initiate.admin_role_assign",
                    "initiate.user_access",
                    "authorize"
            )),

    /** Assigns governance roles to admin users. */
    CONTROL("Control",
            "Classifies administrator accounts and controls their access - who becomes an initiator or an authorizer, and who is suspended, reinstated or time-locked.",
            List.of(
                    "view.dashboard", "view.services", "view.users", "view.approvals", "view.role_matrix",
                    "initiate.admin_user_create", "initiate.admin_role_assign", "initiate.user_access"
            )),

    /** Initiates actions (vendor registration, service assignments, ...). */
    INITIATOR("Initiator",
            "Initiates actions such as vendor registration and service assignments. Cannot authorize.",
            List.of(
                    "view.dashboard", "view.clients", "view.services",
                    "view.approvals", "view.role_matrix",
                    "initiate.vendor_registration", "initiate.service_assignment",
                    "initiate.client_suspend", "initiate.client_activate"
            )),

    /** Authorizes actions initiated by others. Cannot initiate. */
    AUTHORIZER("Authorizer",
            "Authorizes actions initiated by others. Cannot initiate any action.",
            List.of(
                    "view.dashboard", "view.clients", "view.services", "view.role_requests",
                    "view.approvals", "view.role_matrix",
                    "authorize"
            ));

    private final String label;
    private final String description;
    private final List<String> permissions;

    AdminRole(String label, String description, List<String> permissions) {
        this.label = label;
        this.description = description;
        this.permissions = permissions;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    /** Capability keys the UI maps to screens, buttons and API calls. */
    public List<String> getPermissions() {
        return permissions;
    }

    public boolean can(String permission) {
        return permissions.contains(permission);
    }
}
