package com.bewerbung.dto;

public class GenerateResponseDto {
    private String coverLetter;
    private Double matchingScore;

    public GenerateResponseDto() {
    }

    public GenerateResponseDto(String coverLetter) {
        this.coverLetter = coverLetter;
    }

    public GenerateResponseDto(String coverLetter, Double matchingScore) {
        this.coverLetter = coverLetter;
        this.matchingScore = matchingScore;
    }

    public String getCoverLetter() {
        return coverLetter;
    }

    public void setCoverLetter(String coverLetter) {
        this.coverLetter = coverLetter;
    }

    public Double getMatchingScore() {
        return matchingScore;
    }

    public void setMatchingScore(Double matchingScore) {
        this.matchingScore = matchingScore;
    }
}

