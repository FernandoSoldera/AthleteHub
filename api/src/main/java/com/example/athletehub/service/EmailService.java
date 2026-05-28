package com.example.athletehub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails. Plain text only at MVP — HTML templates can come
 * later. Failures bubble up to the caller, which is expected to treat email as
 * a best-effort secondary concern (the primary action — e.g. issuing the reset
 * code — should already have committed).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@athletehub.app}")
    private String from;

    public void sendPlainText(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    public void sendPasswordResetCode(String to, String code) {
        String body = """
                You requested a password reset for your AthleteHub account.

                Your reset code is: %s

                The code expires in 15 minutes. If you didn't request this, you can ignore this email.

                — AthleteHub
                """.formatted(code);
        sendPlainText(to, "AthleteHub — Password reset code", body);
    }
}
