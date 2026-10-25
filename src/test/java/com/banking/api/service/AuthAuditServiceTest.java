package com.banking.api.service;

import com.banking.api.entity.DownstreamAudit;
import com.banking.api.repository.DownstreamAuditRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuthAuditServiceTest {

    private final DownstreamAuditRepository repository = mock(DownstreamAuditRepository.class);
    private final AuthAuditService service = new AuthAuditService(repository);

    @Test
    void storesRequestAndResponseWithoutPasswordOrTokens() {
        service.record("login", "jane@bank.com",
                JsonNodeFactory.instance.objectNode().put("username", "jane@bank.com").put("password", "secret"),
                JsonNodeFactory.instance.objectNode().put("access_token", "abc").put("refresh_token", "def"),
                200, LocalDateTime.now());

        ArgumentCaptor<DownstreamAudit> captor = ArgumentCaptor.forClass(DownstreamAudit.class);
        verify(repository).save(captor.capture());
        DownstreamAudit row = captor.getValue();
        assertEquals("login", row.getOperation());
        assertEquals("jane@bank.com", row.getUserId());
        assertEquals(200, row.getHttpStatus());
        assertTrue(row.getRequest().contains("jane@bank.com"));
        assertFalse(row.getRequest().contains("secret"));
        assertFalse(row.getResponse().contains("abc"));
        assertFalse(row.getResponse().contains("def"));
    }
}
