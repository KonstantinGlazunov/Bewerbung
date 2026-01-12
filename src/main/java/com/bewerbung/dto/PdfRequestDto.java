package com.bewerbung.dto;

public class PdfRequestDto {
    private String coverLetter;

    public PdfRequestDto() {
    }

    public PdfRequestDto(String coverLetter) {
        this.coverLetter = coverLetter;
    }

    public String getCoverLetter() {
        return coverLetter;
    }

    public void setCoverLetter(String coverLetter) {
        this.coverLetter = coverLetter;
    }
}

