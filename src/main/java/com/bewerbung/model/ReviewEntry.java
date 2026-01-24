package com.bewerbung.model;

public class ReviewEntry {
    private String review;
    private String createdAt;

    public ReviewEntry() {
    }

    public ReviewEntry(String review, String createdAt) {
        this.review = review;
        this.createdAt = createdAt;
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
}

