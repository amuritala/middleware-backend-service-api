package com.banking.api.entity;

/** PENDING until an authoriser approves (user is created) or rejects (nothing is created). */
public enum UserCreationRequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}
