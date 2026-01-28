package com.bewerbung.model;

public class ReviewEntry {
    private String review;
    private String createdAt;
    private String source;

    public ReviewEntry() {
    }

    public ReviewEntry(String review, String createdAt) {
        this.review = review;
        this.createdAt = createdAt;
    }

    public ReviewEntry(String review, String createdAt, String source) {
        this.review = review;
        this.createdAt = createdAt;
        this.source = source;
    }

    public String getReview() {
        return review;
    }

    public void setReview(String review) {
        this.review = review;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}

