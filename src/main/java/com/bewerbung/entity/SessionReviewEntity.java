package com.bewerbung.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Один отзыв в рамках сессии. Хранится в Oracle при активном профиле oracle.
 */
@Entity
@Table(name = "BEWERB_SESSION_REVIEW",
       indexes = @Index(name = "IDX_BEWERB_REV_SESS", columnList = "session_id"))
public class SessionReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Lob
    @Column(name = "review_txt", nullable = false)
    private String reviewText;

    @Column(name = "source", length = 256)
    private String source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void setCreatedAt() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getReviewText() { return reviewText; }
    public void setReviewText(String reviewText) { this.reviewText = reviewText; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
