package com.banking.api.entity;

/**
 * Review status of a vendor's role-change application: PENDING until an administrator
 * approves it (roles granted) or rejects it (roles unchanged).
 */
public enum RoleChangeApplicationStatus {
    PENDING,
    APPROVED,
    REJECTED
}
