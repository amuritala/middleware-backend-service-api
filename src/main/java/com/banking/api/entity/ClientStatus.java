package com.banking.api.entity;

/**
 * Lifecycle of a client: freshly registered (PENDING), then reviewed (APPROVED / REJECTED),
 * and once operating, ACTIVE, SUSPENDED or INACTIVE.
 */
public enum ClientStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED,
    ACTIVE,
    INACTIVE
}

