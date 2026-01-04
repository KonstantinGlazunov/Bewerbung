package com.bewerbung.model;

import java.util.List;

public class JobRequirements {
    private String position;
    private String company;
    private String location;
    private List<String> requiredSkills;
    private List<String> preferredSkills;
    private String education;
    private String experience;
    private List<String> languages;

    public JobRequirements() {
    }

    public JobRequirements(String position, String company, String location, 
                          List<String> requiredSkills, List<String> preferredSkills,
                          String education, String experience, List<String> languages) {
        this.position = position;
        this.company = company;
        this.location = location;
        this.requiredSkills = requiredSkills;
        this.preferredSkills = preferredSkills;
        this.education = education;
        this.experience = experience;
        this.languages = languages;
    }

    // Getters and Setters
    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public List<String> getRequiredSkills() {
        return requiredSkills;
    }

    public void setRequiredSkills(List<String> requiredSkills) {
        this.requiredSkills = requiredSkills;
    }

    public List<String> getPreferredSkills() {
        return preferredSkills;
    }

    public void setPreferredSkills(List<String> preferredSkills) {
        this.preferredSkills = preferredSkills;
    }

    public String getEducation() {
        return education;
    }

    public void setEducation(String education) {
        this.education = education;
    }

    public String getExperience() {
        return experience;
    }

    public void setExperience(String experience) {
        this.experience = experience;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public void setLanguages(List<String> languages) {
        this.languages = languages;
    }
}

