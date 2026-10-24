package com.banking.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WelcomeEmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    public boolean sendTemporaryPassword(
            String email,
            String firstName,
            String temporaryPassword) {

        try {
            SimpleMailMessage message = new SimpleMailMessage();

            if (fromEmail != null && !fromEmail.isBlank()) {
                message.setFrom(fromEmail);
            }

            message.setTo(email);
            message.setSubject("Your Bank Portal Account");
            message.setText(
                    "Hello " + firstName + ",\n\n"
                    + "Your bank portal account has been created.\n\n"
                    + "Your temporary password is: " + temporaryPassword + "\n\n"
                    + "Please sign in and change your password immediately.\n\n"
                    + "Regards,\nBank Support Team"
            );

            mailSender.send(message);

            log.info("Welcome email sent to {}", email);
            return true;

        } catch (MailException | IllegalStateException exception) {
            log.error("Failed to send welcome email to {}: {}",
                    email, exception.getMessage());

            return false;
        }
    }
}