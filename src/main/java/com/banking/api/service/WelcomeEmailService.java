package com.banking.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WelcomeEmailService {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;
    private final String fromName;
    private final String loginUrl;

    public WelcomeEmailService(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${app.mail.from}") String from,
            @Value("${app.mail.from-name}") String fromName,
            @Value("${app.login-url}") String loginUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.fromName = fromName;
        this.loginUrl = loginUrl;
    }

    /**
     * Emails an admin-created user their temporary password. Never throws: the account already
     * exists, so a mail problem is reported through the return value instead.
     *
     * @return true when the message was handed to the mail server
     */
    public boolean sendTemporaryPassword(String to, String firstName, String temporaryPassword) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Mail is not configured (set MAIL_HOST); welcome email to {} was not sent", to);
            return false;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromName + " <" + from + ">");
        message.setTo(to);
        message.setSubject("Your " + fromName + " account has been created");
        message.setText(buildBody(to, firstName, temporaryPassword));

        try {
            sender.send(message);
            log.info("Welcome email sent to {}", to);
            return true;
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", to, e.getMessage());
            return false;
        }
    }

    String buildBody(String username, String firstName, String temporaryPassword) {
        return """
                Hello %s,

                An administrator has created a %s account for you.

                Sign in at: %s

                Username: %s
                Temporary password: %s

                For your security you must choose a new password the first time you sign in. \
                Do not share this email or the temporary password with anyone.

                If you were not expecting this account, please contact your administrator.
                """.formatted(firstName, fromName, loginUrl, username, temporaryPassword);
    }
}
