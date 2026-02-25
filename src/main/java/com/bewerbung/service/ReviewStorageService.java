package com.bewerbung.service;

import com.bewerbung.model.ReviewEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ReviewStorageService {
    private static final Logger logger = LoggerFactory.getLogger(ReviewStorageService.class);

    private final EmailService emailService;
    private final SessionStorageService sessionStorage;

    public ReviewStorageService(EmailService emailService, SessionStorageService sessionStorage) {
        this.emailService = emailService;
        this.sessionStorage = sessionStorage;
    }

    public ReviewEntry saveReview(String sessionId, String review, String userInfo) {
        return saveReview(sessionId, review, userInfo, null);
    }

    public ReviewEntry saveReview(String sessionId, String review, String userInfo, String source) {
        String trimmedReview = review == null ? "" : review.trim();
        if (trimmedReview.isEmpty()) {
            throw new IllegalArgumentException("Review must not be blank");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session ID is required to save review");
        }
        String createdAt = Instant.now().toString();
        ReviewEntry entry = new ReviewEntry(trimmedReview, createdAt, source);
        sessionStorage.addReview(sessionId, trimmedReview, source);
        emailService.sendReviewEmail(trimmedReview, createdAt, userInfo, source);
        return entry;
    }
}

