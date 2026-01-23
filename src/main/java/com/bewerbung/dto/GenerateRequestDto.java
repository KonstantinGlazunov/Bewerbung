package com.bewerbung.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public class GenerateRequestDto {
    @NotNull(message = "Biography must not be null")
    @Valid
    private Map<String, Object> biography;
    
    @NotBlank(message = "Job posting must not be blank")
    private String jobPosting;
    
    private String wishes;

    public GenerateRequestDto() {
    }

    public GenerateRequestDto(Map<String, Object> biography, String jobPosting) {
        this.biography = biography;
        this.jobPosting = jobPosting;
    }

    public GenerateRequestDto(Map<String, Object> biography, String jobPosting, String wishes) {
        this.biography = biography;
        this.jobPosting = jobPosting;
        this.wishes = wishes;
    }

    public Map<String, Object> getBiography() {
        return biography;
    }

    public void setBiography(Map<String, Object> biography) {
        this.biography = biography;
    }

    public String getJobPosting() {
        return jobPosting;
    }

    public void setJobPosting(String jobPosting) {
        this.jobPosting = jobPosting;
    }

    public String getWishes() {
        return wishes;
    }

    public void setWishes(String wishes) {
        this.wishes = wishes;
    }
}

