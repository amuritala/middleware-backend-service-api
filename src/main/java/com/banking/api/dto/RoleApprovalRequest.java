package com.banking.api.dto;

import java.util.Set;

/** Optional body on approval: newly requested roles the authoriser declines. */
public record RoleApprovalRequest(Set<String> rejectedRoles) {
}
