package com.bewerbung.dto;

public class CoverLetterResponseDto {
    private String coverLetter;

    public CoverLetterResponseDto() {
    }

    public CoverLetterResponseDto(String coverLetter) {
        this.coverLetter = coverLetter;
    }

    public String getCoverLetter() {
        return coverLetter;
    }

    public void setCoverLetter(String coverLetter) {
        this.coverLetter = coverLetter;
    }
}

