package com.banking.api.entity;

/**
 * The kinds of action that travel through the maker-checker flow. Every type names who may
 * initiate it and who may authorize it; {@code commentRequired} marks the types where both
 * the initiation and the decision must carry a written comment. Vendor registration and
 * admin user creation are exempt because their forms already capture the full context.
 */
public enum ApprovalActionType {

    VENDOR_REGISTRATION("Vendor registration", false),
    ADMIN_USER_CREATE("Admin user creation", false),
    SERVICE_ASSIGNMENT("Service assignment", true),
    CLIENT_SUSPEND("Vendor suspension", true),
    CLIENT_ACTIVATE("Vendor activation", true),
    ADMIN_ROLE_ASSIGN("Admin role assignment", true),
    USER_SUSPEND("User suspension", true),
    USER_UNSUSPEND("User reinstatement", true),
    USER_LOCK("User time lock", true);

    private final String label;
    private final boolean commentRequired;

    ApprovalActionType(String label, boolean commentRequired) {
        this.label = label;
        this.commentRequired = commentRequired;
    }

    public String getLabel() {
        return label;
    }

    /** Whether initiation and authorization both demand a written comment. */
    public boolean isCommentRequired() {
        return commentRequired;
    }
}
