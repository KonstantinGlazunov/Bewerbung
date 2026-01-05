package com.bewerbung.service;

import com.bewerbung.model.Biography;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class BiographyService {

    private static final Logger logger = LoggerFactory.getLogger(BiographyService.class);
    private static final String BIOGRAPHY_FILE_PATH = "input/biography.json";
    
    private final Gson gson;

    public BiographyService() {
        this.gson = new Gson();
    }

    public Biography loadBiography() {
        logger.info("Loading biography from: {}", BIOGRAPHY_FILE_PATH);
        
        try {
            ClassPathResource resource = new ClassPathResource(BIOGRAPHY_FILE_PATH);
            
            if (!resource.exists()) {
                throw new RuntimeException("Biography file not found: " + BIOGRAPHY_FILE_PATH);
            }
            
            JsonObject jsonObject;
            try (InputStreamReader reader = new InputStreamReader(
                    resource.getInputStream(), StandardCharsets.UTF_8)) {
                jsonObject = gson.fromJson(reader, JsonObject.class);
            }
            
            Biography biography = parseBiography(jsonObject);
            
            logger.info("Successfully loaded biography for: {}", biography.getName());
            return biography;
            
        } catch (IOException e) {
            logger.error("Error reading biography file: {}", BIOGRAPHY_FILE_PATH, e);
            throw new RuntimeException("Failed to load biography: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error parsing biography JSON", e);
            throw new RuntimeException("Failed to parse biography: " + e.getMessage(), e);
        }
    }

    private Biography parseBiography(JsonObject jsonObject) {
        Biography biography = new Biography();
        
        // Parse personal information
        JsonObject personalInfo = jsonObject.getAsJsonObject("personalInformation");
        if (personalInfo != null) {
            String firstName = personalInfo.has("firstName") ? 
                    personalInfo.get("firstName").getAsString() : "";
            String lastName = personalInfo.has("lastName") ? 
                    personalInfo.get("lastName").getAsString() : "";
            biography.setName((firstName + " " + lastName).trim());
            biography.setEmail(personalInfo.has("email") ? 
                    personalInfo.get("email").getAsString() : "");
            biography.setPhone(personalInfo.has("phone") ? 
                    personalInfo.get("phone").getAsString() : "");
            biography.setDateOfBirth(personalInfo.has("birthDate") ? 
                    personalInfo.get("birthDate").getAsString() : "");
            biography.setNationality(personalInfo.has("nationality") ? 
                    personalInfo.get("nationality").getAsString() : "");
            
            // Parse address
            JsonObject addressObj = personalInfo.getAsJsonObject("address");
            if (addressObj != null) {
                StringBuilder addressBuilder = new StringBuilder();
                if (addressObj.has("street")) {
                    addressBuilder.append(addressObj.get("street").getAsString());
                }
                if (addressObj.has("postalCode")) {
                    if (addressBuilder.length() > 0) addressBuilder.append(", ");
                    addressBuilder.append(addressObj.get("postalCode").getAsString());
                }
                if (addressObj.has("city")) {
                    if (addressBuilder.length() > 0) addressBuilder.append(" ");
                    addressBuilder.append(addressObj.get("city").getAsString());
                }
                if (addressObj.has("country")) {
                    if (addressBuilder.length() > 0) addressBuilder.append(", ");
                    addressBuilder.append(addressObj.get("country").getAsString());
                }
                biography.setAddress(addressBuilder.toString());
            }
        }
        
        // Parse education
        JsonArray educationArray = jsonObject.getAsJsonArray("education");
        if (educationArray != null) {
            List<Biography.Education> educationList = new ArrayList<>();
            for (int i = 0; i < educationArray.size(); i++) {
                JsonObject eduObj = educationArray.get(i).getAsJsonObject();
                Biography.Education education = new Biography.Education();
                education.setDegree(eduObj.has("degree") ? 
                        eduObj.get("degree").getAsString() : "");
                if (eduObj.has("field") && !education.getDegree().isEmpty()) {
                    education.setDegree(education.getDegree() + " - " + 
                            eduObj.get("field").getAsString());
                }
                education.setInstitution(eduObj.has("university") ? 
                        eduObj.get("university").getAsString() : "");
                education.setLocation(eduObj.has("location") ? 
                        eduObj.get("location").getAsString() : "");
                education.setStartDate(eduObj.has("startDate") ? 
                        eduObj.get("startDate").getAsString() : "");
                education.setEndDate(eduObj.has("endDate") ? 
                        eduObj.get("endDate").getAsString() : "");
                education.setDescription(eduObj.has("description") ? 
                        eduObj.get("description").getAsString() : "");
                educationList.add(education);
            }
            biography.setEducation(educationList);
        }
        
        // Parse work experience
        JsonArray workExpArray = jsonObject.getAsJsonArray("workExperience");
        if (workExpArray != null) {
            List<Biography.WorkExperience> workExpList = new ArrayList<>();
            for (int i = 0; i < workExpArray.size(); i++) {
                JsonObject workObj = workExpArray.get(i).getAsJsonObject();
                Biography.WorkExperience workExp = new Biography.WorkExperience();
                workExp.setPosition(workObj.has("position") ? 
                        workObj.get("position").getAsString() : "");
                workExp.setCompany(workObj.has("company") ? 
                        workObj.get("company").getAsString() : "");
                workExp.setLocation(workObj.has("location") ? 
                        workObj.get("location").getAsString() : "");
                workExp.setStartDate(workObj.has("startDate") ? 
                        workObj.get("startDate").getAsString() : "");
                workExp.setEndDate(workObj.has("endDate") ? 
                        workObj.get("endDate").getAsString() : "");
                
                // Combine description and technologies as responsibilities
                StringBuilder responsibilities = new StringBuilder();
                if (workObj.has("description")) {
                    responsibilities.append(workObj.get("description").getAsString());
                }
                if (workObj.has("technologies")) {
                    JsonArray techArray = workObj.getAsJsonArray("technologies");
                    if (techArray != null && techArray.size() > 0) {
                        if (responsibilities.length() > 0) {
                            responsibilities.append(" ");
                        }
                        List<String> techList = StreamSupport.stream(techArray.spliterator(), false)
                                .map(e -> e.getAsString())
                                .collect(Collectors.toList());
                        responsibilities.append("Technologien: ").append(String.join(", ", techList));
                    }
                }
                workExp.setResponsibilities(responsibilities.toString());
                workExpList.add(workExp);
            }
            biography.setWorkExperience(workExpList);
        }
        
        // Parse skills
        JsonObject skillsObj = jsonObject.getAsJsonObject("skills");
        if (skillsObj != null) {
            List<String> technicalSkills = new ArrayList<>();
            
            // Combine programming languages, frameworks, databases, and tools as technical skills
            if (skillsObj.has("programmingLanguages")) {
                JsonArray progLangArray = skillsObj.getAsJsonArray("programmingLanguages");
                if (progLangArray != null) {
                    StreamSupport.stream(progLangArray.spliterator(), false)
                            .forEach(e -> technicalSkills.add(e.getAsString()));
                }
            }
            if (skillsObj.has("frameworks")) {
                JsonArray frameworksArray = skillsObj.getAsJsonArray("frameworks");
                if (frameworksArray != null) {
                    StreamSupport.stream(frameworksArray.spliterator(), false)
                            .forEach(e -> technicalSkills.add(e.getAsString()));
                }
            }
            if (skillsObj.has("databases")) {
                JsonArray databasesArray = skillsObj.getAsJsonArray("databases");
                if (databasesArray != null) {
                    StreamSupport.stream(databasesArray.spliterator(), false)
                            .forEach(e -> technicalSkills.add(e.getAsString()));
                }
            }
            if (skillsObj.has("tools")) {
                JsonArray toolsArray = skillsObj.getAsJsonArray("tools");
                if (toolsArray != null) {
                    StreamSupport.stream(toolsArray.spliterator(), false)
                            .forEach(e -> technicalSkills.add(e.getAsString()));
                }
            }
            biography.setTechnicalSkills(technicalSkills);
            
            // Parse languages
            if (skillsObj.has("languages")) {
                JsonArray languagesArray = skillsObj.getAsJsonArray("languages");
                if (languagesArray != null) {
                    List<Biography.Language> languageList = new ArrayList<>();
                    for (int i = 0; i < languagesArray.size(); i++) {
                        JsonObject langObj = languagesArray.get(i).getAsJsonObject();
                        Biography.Language language = new Biography.Language();
                        language.setLanguage(langObj.has("language") ? 
                                langObj.get("language").getAsString() : "");
                        language.setLevel(langObj.has("level") ? 
                                langObj.get("level").getAsString() : "");
                        languageList.add(language);
                    }
                    biography.setLanguages(languageList);
                }
            }
            
            // Soft skills could be extracted from additionalInfo if needed
            biography.setSoftSkills(new ArrayList<>());
        }
        
        // Parse certifications
        JsonArray certArray = jsonObject.getAsJsonArray("certifications");
        if (certArray != null) {
            List<Biography.Certification> certList = new ArrayList<>();
            for (int i = 0; i < certArray.size(); i++) {
                JsonObject certObj = certArray.get(i).getAsJsonObject();
                Biography.Certification certification = new Biography.Certification();
                certification.setName(certObj.has("name") ? 
                        certObj.get("name").getAsString() : "");
                certification.setIssuer(certObj.has("issuer") ? 
                        certObj.get("issuer").getAsString() : "");
                certification.setDate(certObj.has("date") ? 
                        certObj.get("date").getAsString() : "");
                certList.add(certification);
            }
            biography.setCertifications(certList);
        }
        
        return biography;
    }
}

