package com.banking.api.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WelcomeEmailServiceTest {

    @SuppressWarnings("unchecked")
    private WelcomeEmailService service(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return new WelcomeEmailService(provider, "noreply@skybank.com", "SkyBank", "https://app.skybank.com/login");
    }

    @Test
    void emailsUsernamePasswordAndLoginUrl() {
        JavaMailSender sender = mock(JavaMailSender.class);

        boolean sent = service(sender).sendTemporaryPassword("jane@acme.com", "Jane", "Temp#1234");

        assertTrue(sent);
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertEquals("jane@acme.com", message.getTo()[0]);
        assertEquals("SkyBank <noreply@skybank.com>", message.getFrom());
        assertTrue(message.getText().contains("Temp#1234"));
        assertTrue(message.getText().contains("jane@acme.com"));
        assertTrue(message.getText().contains("https://app.skybank.com/login"));
        assertTrue(message.getText().contains("must choose a new password"));
        assertFalse(message.getSubject().contains("Temp#1234"));
    }

    @Test
    void returnsFalseWhenMailIsNotConfigured() {
        assertFalse(service(null).sendTemporaryPassword("jane@acme.com", "Jane", "Temp#1234"));
    }

    @Test
    void returnsFalseWithoutThrowingWhenSendingFails() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("smtp down")).when(sender).send(any(SimpleMailMessage.class));

        assertFalse(service(sender).sendTemporaryPassword("jane@acme.com", "Jane", "Temp#1234"));
    }
}
