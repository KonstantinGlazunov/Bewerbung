package com.bewerbung.dto;

import jakarta.validation.constraints.NotBlank;

public class ReviewRequestDto {
    @NotBlank(message = "Review must not be blank")
    private String review;
    
    private String userInfo;
    
    private String source;

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
    
    public String getUserInfo() {
        return userInfo;
    }
    
    public void setUserInfo(String userInfo) {
        this.userInfo = userInfo;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
}

