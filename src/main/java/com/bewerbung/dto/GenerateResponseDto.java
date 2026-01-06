package com.bewerbung.dto;

public class GenerateResponseDto {
    private String coverLetter;
    private String cv;
    private Double matchingScore;

    public GenerateResponseDto() {
    }

    public GenerateResponseDto(String coverLetter, String cv) {
        this.coverLetter = coverLetter;
        this.cv = cv;
    }

    public GenerateResponseDto(String coverLetter, String cv, Double matchingScore) {
        this.coverLetter = coverLetter;
        this.cv = cv;
        this.matchingScore = matchingScore;
    }

    public String getCoverLetter() {
        return coverLetter;
    }

    public void setCoverLetter(String coverLetter) {
        this.coverLetter = coverLetter;
    }

    public String getCv() {
        return cv;
    }

    public void setCv(String cv) {
        this.cv = cv;
    }

    public Double getMatchingScore() {
        return matchingScore;
    }

    public void setMatchingScore(Double matchingScore) {
        this.matchingScore = matchingScore;
    }
}

