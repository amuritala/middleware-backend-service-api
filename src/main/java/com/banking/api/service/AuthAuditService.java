package com.banking.api.service;

import com.banking.api.entity.DownstreamAudit;
import com.banking.api.repository.DownstreamAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Writes one downstream-audit row per authentication call. Credentials and tokens are redacted
 * before they are stored; an audit failure never affects the sign-in itself.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthAuditService {

    private static final String MASK = "********";
    private static final String REDACTED = "[REDACTED]";

    private final DownstreamAuditRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void record(String operation, String userId, ObjectNode request, ObjectNode response,
                       int httpStatus, LocalDateTime start) {
        try {
            mask(request, "password", MASK);
            mask(request, "refresh_token", REDACTED);
            mask(response, "access_token", REDACTED);
            mask(response, "refresh_token", REDACTED);

            DownstreamAudit audit = new DownstreamAudit();
            audit.setUserId(userId);
            audit.setOperation(operation);
            audit.setRequest(objectMapper.writeValueAsString(request));
            audit.setResponse(response == null ? null : objectMapper.writeValueAsString(response));
            audit.setHttpStatus(httpStatus);
            audit.setStartTime(start);
            audit.setEndTime(LocalDateTime.now());
            repository.save(audit);
        } catch (Exception e) {
            log.warn("Could not write auth audit row for {}: {}", operation, e.getMessage());
        }
    }

    private static void mask(ObjectNode node, String field, String value) {
        if (node != null && node.has(field)) {
            node.put(field, value);
        }
    }
}
