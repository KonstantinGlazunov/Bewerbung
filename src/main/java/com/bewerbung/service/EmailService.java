package com.bewerbung.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;

@Service
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    
    @Value("${email.api.provider:brevo}")
    private String emailProvider;
    
    @Value("${email.api.key:}")
    private String apiKey;
    
    @Value("${email.from.address:100polok2018@gmail.com}")
    private String fromEmail;
    
    @Value("${email.from.name:Bewerbung AI}")
    private String fromName;
    
    @Value("${review.email.recipient:kglaz@ya.ru}")
    private String recipientEmail;
    
    @Value("${review.email.enabled:true}")
    private boolean emailEnabled;
    
    @Value("${email.mailgun.domain:}")
    private String mailgunDomain;
    
    private final WebClient webClient;
    private final Gson gson;
    
    public EmailService() {
        this.webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.gson = new Gson();
    }
    
    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.warn("Email API key is not configured. Set EMAIL_API_KEY environment variable. Email sending will be disabled.");
            this.emailEnabled = false;
        } else if (fromEmail == null || recipientEmail == null) {
            logger.warn("Email configuration incomplete! From: {}, To: {}. Email sending will be disabled.", fromEmail, recipientEmail);
            this.emailEnabled = false;
        } else {
            logger.info("EmailService initialized. Provider: {}, From: {} ({}), To: {}, Enabled: {}", 
                    emailProvider, fromEmail, fromName, recipientEmail, emailEnabled);
        }
    }
    
    @Async("taskExecutor")
    public void sendReviewEmail(String reviewText, String createdAt, String userInfo) {
        if (!emailEnabled) {
            logger.debug("Email sending is disabled. Skipping email notification.");
            return;
        }
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.warn("Email API key is not configured. Set EMAIL_API_KEY environment variable.");
            return;
        }
        
        if (fromEmail == null || recipientEmail == null) {
            logger.warn("Email configuration incomplete. From: {}, To: {}", fromEmail, recipientEmail);
            return;
        }
        
        try {
            String emailBody = buildEmailBody(reviewText, createdAt, userInfo);
            
            switch (emailProvider.toLowerCase()) {
                case "brevo":
                    sendViaBrevo(emailBody);
                    break;
                case "sendgrid":
                    sendViaSendGrid(emailBody);
                    break;
                case "mailgun":
                    sendViaMailgun(emailBody);
                    break;
                default:
                    logger.error("Unknown email provider: {}. Supported: brevo, sendgrid, mailgun", emailProvider);
            }
        } catch (Exception e) {
            logger.error("Failed to send review email to {}", recipientEmail, e);
            // Не пробрасываем исключение, чтобы не прерывать сохранение отзыва
        }
    }
    
    private String buildEmailBody(String reviewText, String createdAt, String userInfo) {
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
        
        return emailBody.toString();
    }
    
    private void sendViaBrevo(String emailBody) {
        JsonObject requestBody = new JsonObject();
        JsonObject sender = new JsonObject();
        sender.addProperty("email", fromEmail);
        sender.addProperty("name", fromName);
        requestBody.add("sender", sender);
        
        JsonObject to = new JsonObject();
        to.addProperty("email", recipientEmail);
        requestBody.add("to", new com.google.gson.JsonArray());
        requestBody.getAsJsonArray("to").add(to);
        
        requestBody.addProperty("subject", "Новый отзыв на Bewerbung AI");
        requestBody.addProperty("textContent", emailBody);
        
        String jsonBody = gson.toJson(requestBody);
        
        webClient.post()
                .uri("https://api.brevo.com/v3/smtp/email")
                .header("api-key", apiKey)
                .bodyValue(jsonBody)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .filter(throwable -> {
                            // Retry on network errors, but not on 4xx errors
                            if (throwable instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                                var ex = (org.springframework.web.reactive.function.client.WebClientResponseException) throwable;
                                return ex.getStatusCode().is5xxServerError();
                            }
                            return true;
                        }))
                .doOnSuccess(response -> logger.info("Review email sent successfully via Brevo to {}", recipientEmail))
                .doOnError(error -> logger.error("Failed to send email via Brevo to {}: {}", recipientEmail, error.getMessage()))
                .subscribe();
    }
    
    private void sendViaSendGrid(String emailBody) {
        JsonObject requestBody = new JsonObject();
        JsonObject from = new JsonObject();
        from.addProperty("email", fromEmail);
        from.addProperty("name", fromName);
        requestBody.add("from", from);
        
        JsonObject to = new JsonObject();
        to.addProperty("email", recipientEmail);
        requestBody.add("to", new com.google.gson.JsonArray());
        requestBody.getAsJsonArray("to").add(to);
        
        requestBody.addProperty("subject", "Новый отзыв на Bewerbung AI");
        requestBody.add("content", new com.google.gson.JsonArray());
        JsonObject content = new JsonObject();
        content.addProperty("type", "text/plain");
        content.addProperty("value", emailBody);
        requestBody.getAsJsonArray("content").add(content);
        
        String jsonBody = gson.toJson(requestBody);
        
        webClient.post()
                .uri("https://api.sendgrid.com/v3/mail/send")
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(jsonBody)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .filter(throwable -> {
                            if (throwable instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                                var ex = (org.springframework.web.reactive.function.client.WebClientResponseException) throwable;
                                return ex.getStatusCode().is5xxServerError();
                            }
                            return true;
                        }))
                .doOnSuccess(response -> logger.info("Review email sent successfully via SendGrid to {}", recipientEmail))
                .doOnError(error -> logger.error("Failed to send email via SendGrid to {}: {}", recipientEmail, error.getMessage()))
                .subscribe();
    }
    
    private void sendViaMailgun(String emailBody) {
        // Mailgun uses form data, not JSON
        String formData = String.format(
                "from=%s <%s>&to=%s&subject=%s&text=%s",
                fromName, fromEmail, recipientEmail,
                "Новый отзыв на Bewerbung AI",
                emailBody.replace("\n", "%0A")
        );
        
        webClient.post()
                .uri("https://api.mailgun.net/v3/{domain}/messages", getMailgunDomain())
                .header(HttpHeaders.AUTHORIZATION, "Basic " + java.util.Base64.getEncoder()
                        .encodeToString(("api:" + apiKey).getBytes()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(formData)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .filter(throwable -> {
                            if (throwable instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                                var ex = (org.springframework.web.reactive.function.client.WebClientResponseException) throwable;
                                return ex.getStatusCode().is5xxServerError();
                            }
                            return true;
                        }))
                .doOnSuccess(response -> logger.info("Review email sent successfully via Mailgun to {}", recipientEmail))
                .doOnError(error -> logger.error("Failed to send email via Mailgun to {}: {}", recipientEmail, error.getMessage()))
                .subscribe();
    }
    
    private String getMailgunDomain() {
        return mailgunDomain != null && !mailgunDomain.isEmpty() 
                ? mailgunDomain 
                : fromEmail.substring(fromEmail.indexOf("@") + 1);
    }
}
