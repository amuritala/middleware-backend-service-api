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
        when(repository.findAll(PageRequest.of(0, 10))).thenReturn(audits);

        Page<DownstreamAuditResponse> result =
                new DownstreamAuditService(repository).findAll(PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals("queryCustomer", result.getContent().get(0).operation());
        assertEquals("user@example.com", result.getContent().get(0).userId());
    }
}
