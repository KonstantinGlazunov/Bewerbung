package com.bewerbung.dto;

public class CvResponseDto {
    private String cv;

    public CvResponseDto() {
    }

    public CvResponseDto(String cv) {
        this.cv = cv;
    }

    public String getCv() {
        return cv;
    }

    public void setCv(String cv) {
        this.cv = cv;
    }
}

