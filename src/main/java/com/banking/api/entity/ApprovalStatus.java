package com.banking.api.entity;

/** Lifecycle of a maker-checker request: awaiting a decision, or decided. */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}
