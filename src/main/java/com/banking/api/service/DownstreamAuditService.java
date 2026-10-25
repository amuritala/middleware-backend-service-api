package com.banking.api.service;

import com.banking.api.dto.DownstreamAuditResponse;
import com.banking.api.repository.DownstreamAuditRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DownstreamAuditService {

    /**
     * Audit rows record the raw payloads this service sent to downstream systems, and those
     * payloads (e.g. a login call) can carry credentials. Password values are masked before
     * the record leaves this API, so the stored rows stay complete for forensics while the
     * admin console never renders a clear-text secret.
     */
    private static final String SECRET_FIELD = "password";
    private static final String MASK = "********";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /** Fallbacks for payloads that are not JSON: `"password":"x"` and form-encoded `password=x`. */
    private static final Pattern JSON_SECRET =
            Pattern.compile("(?i)(\"password\"\\s*:\\s*)\"(?:\\\\.|[^\"])*\"");
    private static final Pattern FORM_SECRET = Pattern.compile("(?i)(\\bpassword=)[^&\\s]+");

    private final DownstreamAuditRepository repository;

    public Page<DownstreamAuditResponse> findAll(Pageable pageable) {
        Pageable ordered = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "startTime"));
        return repository.findAll(ordered).map(audit -> new DownstreamAuditResponse(
                audit.getId(),
                audit.getUserId(),
                audit.getOperation(),
                maskSecrets(audit.getRequest()),
                maskSecrets(audit.getResponse()),
                audit.getHttpStatus(),
                audit.getStartTime(),
                audit.getEndTime()
        ));
    }

    /**
     * Replaces every value of a {@code password} field with asterisks, at any nesting depth.
     * JSON payloads are parsed so keys are matched exactly; anything else falls back to a
     * pattern match, and payloads with no password come back byte-identical apart from the
     * JSON re-serialisation.
     */
    static String maskSecrets(String payload) {
        if (payload == null || payload.isBlank()) {
            return payload;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            maskNode(root);
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (Exception notJson) {
            String masked = JSON_SECRET.matcher(payload).replaceAll("$1\"" + MASK + "\"");
            return FORM_SECRET.matcher(masked).replaceAll("$1" + MASK);
        }
    }

    private static void maskNode(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            // Replacing the value of an existing key is not a structural change,
            // so the field iterator stays valid while we mutate.
            ObjectNode object = (ObjectNode) node;
            object.fields().forEachRemaining(entry -> {
                if (SECRET_FIELD.equalsIgnoreCase(entry.getKey())) {
                    object.put(entry.getKey(), MASK);
                } else {
                    maskNode(entry.getValue());
                }
            });
        } else if (node.isArray()) {
            node.forEach(DownstreamAuditService::maskNode);
        }
    }
}
