package com.bewerbung.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    
    @Value("${spring.mail.username:100polok2018@gmail.com}")
    private String fromEmail;
    
    @Value("${review.email.recipient:kglaz@ya.ru}")
    private String recipientEmail;
    
    @Value("${review.email.enabled:true}")
    private boolean emailEnabled;
    
    private final JavaMailSender mailSender;
    
    @Autowired
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
        if (fromEmail == null || recipientEmail == null) {
            logger.warn("Email configuration incomplete! From: {}, To: {}. " +
                    "Set EMAIL_USERNAME and review.email.recipient environment variables. " +
                    "Email sending will be disabled.", fromEmail, recipientEmail);
            this.emailEnabled = false;
        } else {
            logger.info("EmailService initialized. From: {}, To: {}, Enabled: {}", fromEmail, recipientEmail, emailEnabled);
        }
    }
    
    @Async("taskExecutor")
    public void sendReviewEmail(String reviewText, String createdAt, String userInfo) {
        if (!emailEnabled) {
            logger.debug("Email sending is disabled. Skipping email notification.");
            return;
        }
        
        if (fromEmail == null || recipientEmail == null) {
            logger.warn("Cannot send email - email configuration is incomplete. From: {}, To: {}", fromEmail, recipientEmail);
            return;
        }
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(recipientEmail);
            message.setSubject("Новый отзыв на Bewerbung AI");
            
            StringBuilder emailBody = new StringBuilder();
            emailBody.append("Получен новый отзыв:\n\n");
            emailBody.append("Отзыв:\n");
            emailBody.append(reviewText);
            emailBody.append("\n\n");
            emailBody.append("Дата создания: ").append(createdAt);
            
            if (userInfo != null && !userInfo.trim().isEmpty()) {
                emailBody.append("\n\n");
                emailBody.append("Информация о пользователе:\n");
                emailBody.append(userInfo);
            }
            
            message.setText(emailBody.toString());
            
            mailSender.send(message);
            logger.info("Review email sent successfully to {}", recipientEmail);
        } catch (MailAuthenticationException e) {
            logger.error("Email authentication failed. Gmail requires an Application-Specific Password. " +
                    "Please create one at https://myaccount.google.com/apppasswords and update EMAIL_PASSWORD in variables.env", e);
            // Не пробрасываем исключение, чтобы не прерывать сохранение отзыва
        } catch (MailException e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("timeout") || errorMsg.contains("Connect timed out") || errorMsg.contains("Couldn't connect"))) {
                logger.error("Email connection failed - cannot reach SMTP server. " +
                        "This might be due to network restrictions on production. " +
                        "Try using port 465 with SSL (set EMAIL_PORT=465 and EMAIL_USE_SSL=true) or check firewall settings.", e);
            } else {
                logger.error("Failed to send review email to {}: {}", recipientEmail, errorMsg, e);
            }
            // Не пробрасываем исключение, чтобы не прерывать сохранение отзыва
        } catch (Exception e) {
            logger.error("Unexpected error while sending review email to {}", recipientEmail, e);
            // Не пробрасываем исключение, чтобы не прерывать сохранение отзыва
        }
    }
}

