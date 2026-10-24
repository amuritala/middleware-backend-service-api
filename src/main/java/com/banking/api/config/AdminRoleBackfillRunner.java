package com.banking.api.config;

import com.banking.api.entity.AdminRole;
import com.banking.api.entity.Role;
import com.banking.api.entity.User;
import com.banking.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time, idempotent promotion: every pre-existing administrator (holder of ROLE_ADMIN)
 * becomes a SUPER_ADMIN so the maker-checker rollout cannot lock anyone out. From then on
 * the Control role assigns the finer governance roles individually.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminRoleBackfillRunner implements CommandLineRunner {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(String... args) {
        int promoted = userRepository.promoteAdminsToSuperAdmin();
        if (promoted > 0) {
            log.info("Backfilled {} existing administrator(s) to SUPER_ADMIN", promoted);
        }
    }
}
