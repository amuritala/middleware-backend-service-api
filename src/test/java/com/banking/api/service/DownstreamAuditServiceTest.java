package com.banking.api.service;

import com.banking.api.dto.DownstreamAuditResponse;
import com.banking.api.entity.DownstreamAudit;
import com.banking.api.repository.DownstreamAuditRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DownstreamAuditServiceTest {

    @Test
    void includesPersistedOperationInAuditResponse() {
        UUID id = UUID.randomUUID();
        LocalDateTime started = LocalDateTime.now().minusSeconds(2);
        LocalDateTime ended = LocalDateTime.now();
        DownstreamAudit audit = new DownstreamAudit();
        audit.setId(id);
        audit.setUserId("user@example.com");
        audit.setOperation("queryCustomer");
        audit.setRequest("{\"custno\":\"001633\"}");
        audit.setResponse("{\"msgstat\":\"SUCCESS\"}");
        audit.setHttpStatus(200);
        audit.setStartTime(started);
        audit.setEndTime(ended);

        DownstreamAuditRepository repository = mock(DownstreamAuditRepository.class);
        Page<DownstreamAudit> audits = new PageImpl<>(List.of(audit), PageRequest.of(0, 10), 1);
        when(repository.findAll(PageRequest.of(0, 10, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "startTime")))).thenReturn(audits);

        Page<DownstreamAuditResponse> result =
                new DownstreamAuditService(repository).findAll(PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals("queryCustomer", result.getContent().get(0).operation());
        assertEquals("user@example.com", result.getContent().get(0).userId());
    }

    @Test
    void masksPasswordInReturnedRequestPayload() {
        LocalDateTime started = LocalDateTime.now().minusSeconds(1);
        LocalDateTime ended = LocalDateTime.now();
        DownstreamAudit audit = new DownstreamAudit();
        audit.setId(UUID.randomUUID());
        audit.setUserId("user@example.com");
        audit.setOperation("login");
        audit.setRequest("{\"username\":\"jane@acme.com\",\"password\":\"Sup3rSecret!\"}");
        audit.setResponse("{\"msgstat\":\"SUCCESS\"}");
        audit.setHttpStatus(200);
        audit.setStartTime(started);
        audit.setEndTime(ended);

        DownstreamAuditRepository repository = mock(DownstreamAuditRepository.class);
        when(repository.findAll(PageRequest.of(0, 10, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "startTime"))))
                .thenReturn(new PageImpl<>(List.of(audit), PageRequest.of(0, 10), 1));

        DownstreamAuditResponse record =
                new DownstreamAuditService(repository).findAll(PageRequest.of(0, 10))
                        .getContent().get(0);

        assertEquals("{\"username\":\"jane@acme.com\",\"password\":\"********\"}", record.request());
        assertFalse(record.request().contains("Sup3rSecret!"));
        assertEquals("{\"msgstat\":\"SUCCESS\"}", record.response());
    }

    @Test
    void masksNestedPasswordsAndLeavesOtherFieldsIntact() {
        String payload = "{\"user\":{\"login\":\"jane\",\"credentials\":{\"password\":\"hunter2\"}},"
                + "\"items\":[{\"Password\":\"abc\"},{\"amount\":5}] }";

        String masked = DownstreamAuditService.maskSecrets(payload);

        assertFalse(masked.contains("hunter2"));
        assertFalse(masked.contains("abc"));
        assertTrue(masked.contains("\"login\":\"jane\""));
        assertTrue(masked.contains("\"amount\":5"));
        assertTrue(masked.contains("\"password\":\"********\""));
    }

    @Test
    void masksFormEncodedAndLeavesPayloadsWithoutPasswordsReadable() {
        assertEquals("username=jane&password=********", DownstreamAuditService.maskSecrets("username=jane&password=hunter2"));
        assertEquals("{\"custno\":\"001633\"}", DownstreamAuditService.maskSecrets("{\"custno\":\"001633\"}"));
        assertEquals("", DownstreamAuditService.maskSecrets(""));
        assertNull(DownstreamAuditService.maskSecrets(null));
    }
}
