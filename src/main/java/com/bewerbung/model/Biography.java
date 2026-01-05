package com.bewerbung.model;

import java.util.List;

public class Biography {
    private String name;
    private String email;
    private String phone;
    private String address;
    private String dateOfBirth;
    private String nationality;
    private List<Education> education;
    private List<WorkExperience> workExperience;
    private List<String> technicalSkills;
    private List<String> softSkills;
    private List<Language> languages;
    private List<Certification> certifications;

    public Biography() {
    }

    public Biography(String name, String email, String phone, String address, 
                    String dateOfBirth, String nationality, List<Education> education,
                    List<WorkExperience> workExperience, List<String> technicalSkills,
                    List<String> softSkills, List<Language> languages,
                    List<Certification> certifications) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.dateOfBirth = dateOfBirth;
        this.nationality = nationality;
        this.education = education;
        this.workExperience = workExperience;
        this.technicalSkills = technicalSkills;
        this.softSkills = softSkills;
        this.languages = languages;
        this.certifications = certifications;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public List<Education> getEducation() {
        return education;
    }

    public void setEducation(List<Education> education) {
        this.education = education;
    }

    public List<WorkExperience> getWorkExperience() {
        return workExperience;
    }

    public void setWorkExperience(List<WorkExperience> workExperience) {
        this.workExperience = workExperience;
    }

    public List<String> getTechnicalSkills() {
        return technicalSkills;
    }

    public void setTechnicalSkills(List<String> technicalSkills) {
        this.technicalSkills = technicalSkills;
    }

    public List<String> getSoftSkills() {
        return softSkills;
    }

    public void setSoftSkills(List<String> softSkills) {
        this.softSkills = softSkills;
    }

    public List<Language> getLanguages() {
        return languages;
    }

    public void setLanguages(List<Language> languages) {
        this.languages = languages;
    }

    public List<Certification> getCertifications() {
        return certifications;
    }

    public void setCertifications(List<Certification> certifications) {
        this.certifications = certifications;
    }

    // Nested classes
    public static class Education {
        private String degree;
        private String institution;
        private String location;
        private String startDate;
        private String endDate;
        private String description;

        public Education() {
        }

        public Education(String degree, String institution, String location,
                        String startDate, String endDate, String description) {
            this.degree = degree;
            this.institution = institution;
            this.location = location;
            this.startDate = startDate;
            this.endDate = endDate;
            this.description = description;
        }

        // Getters and Setters
        public String getDegree() {
            return degree;
        }

        public void setDegree(String degree) {
            this.degree = degree;
        }

        public String getInstitution() {
            return institution;
        }

        public void setInstitution(String institution) {
            this.institution = institution;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getStartDate() {
            return startDate;
        }

        public void setStartDate(String startDate) {
            this.startDate = startDate;
        }

        public String getEndDate() {
            return endDate;
        }

        public void setEndDate(String endDate) {
            this.endDate = endDate;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static class WorkExperience {
        private String position;
        private String company;
        private String location;
        private String startDate;
        private String endDate;
        private String responsibilities;

        public WorkExperience() {
        }

        public WorkExperience(String position, String company, String location,
                             String startDate, String endDate, String responsibilities) {
            this.position = position;
            this.company = company;
            this.location = location;
            this.startDate = startDate;
            this.endDate = endDate;
            this.responsibilities = responsibilities;
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

        public String getStartDate() {
            return startDate;
        }

        public void setStartDate(String startDate) {
            this.startDate = startDate;
        }

        public String getEndDate() {
            return endDate;
        }

        public void setEndDate(String endDate) {
            this.endDate = endDate;
        }

        public String getResponsibilities() {
            return responsibilities;
        }

        public void setResponsibilities(String responsibilities) {
            this.responsibilities = responsibilities;
        }
    }

    public static class Language {
        private String language;
        private String level;

        public Language() {
        }

        public Language(String language, String level) {
            this.language = language;
            this.level = level;
        }

        // Getters and Setters
        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }
    }

    public static class Certification {
        private String name;
        private String issuer;
        private String date;

        public Certification() {
        }

        public Certification(String name, String issuer, String date) {
            this.name = name;
            this.issuer = issuer;
            this.date = date;
        }

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }
    }
}

