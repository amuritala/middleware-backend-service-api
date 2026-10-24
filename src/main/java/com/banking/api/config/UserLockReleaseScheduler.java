package com.banking.api.config;

import com.banking.api.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Releases time locks the moment their window passes: every 30 seconds (and therefore also
 * shortly after a restart) any lock that has run out is cleared and the account re-enabled in
 * Keycloak, so a lock can never outlive the minutes an authorizer approved - even if the
 * application was down when it expired.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserLockReleaseScheduler {

    private final AdminService adminService;

    @Scheduled(fixedDelay = 30_000)
    public void releaseExpiredLocks() {
        try {
            int released = adminService.releaseExpiredLocks();
            if (released > 0) {
                log.info("Released {} expired account lock(s)", released);
            }
        } catch (Exception exception) {
            // Retried on the next tick; a lock is never dropped without the Keycloak side working.
            log.error("Could not release expired locks: {}", exception.getMessage());
        }
    }
}
