package com.banking.api.service;

import com.banking.api.entity.AuditLog;
import com.banking.api.entity.AuditOutcome;
import com.banking.api.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Writes the governance audit trail. Every initiation, approval and rejection is recorded
 * here - who acted, in which governance role, on what, and why. Entries are append-only:
 * nothing ever updates or deletes a row.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public void record(
            String actionType,
            AuditOutcome outcome,
            String summary,
            String detail,
            String targetLabel,
            String actorKeycloakUserId,
            String actorName,
            String actorEmail,
            String actorRole,
            String comment
    ) {
        auditLogRepository.save(AuditLog.builder()
                .actionType(actionType)
                .outcome(outcome)
                .summary(summary)
                .detail(detail)
                .targetLabel(targetLabel)
                .actorKeycloakUserId(actorKeycloakUserId)
                .actorName(actorName)
                .actorEmail(actorEmail)
                .actorRole(actorRole)
                .comment(comment)
                .build());
        log.info("Audit [{}] {} by {} ({})", actionType, outcome, actorName, actorRole);
    }

    public Page<AuditLog> findAll(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
}
