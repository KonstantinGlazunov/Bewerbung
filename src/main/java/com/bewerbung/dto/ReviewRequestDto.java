package com.bewerbung.dto;

import jakarta.validation.constraints.NotBlank;

public class ReviewRequestDto {
    @NotBlank(message = "Review must not be blank")
    private String review;

    public ReviewRequestDto() {
    }

    public ReviewRequestDto(String review) {
        this.review = review;
    }

    public String getReview() {
        return review;
    }

    public void setReview(String review) {
        this.review = review;
    }
}

