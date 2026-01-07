package com.bewerbung.service;

import com.bewerbung.model.Biography;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class BiographyAiAnalyzerService {

    private static final Logger logger = LoggerFactory.getLogger(BiographyAiAnalyzerService.class);
    
    private final GroqAiService groqAiService;
    private final Gson gson;

    @Autowired
    public BiographyAiAnalyzerService(GroqAiService groqAiService) {
        this.groqAiService = groqAiService;
        this.gson = new Gson();
    }

    public Biography parseBiography(String rawText) {
        logger.info("Parsing biography from free-form text using AI");
        
        if (rawText == null || rawText.trim().isEmpty()) {
            throw new IllegalArgumentException("Biography text must not be empty");
        }
        
        try {
            // Build prompt for AI to extract structured data
            String prompt = buildExtractionPrompt(rawText);
            
            // Get AI response
            String aiResponse = groqAiService.generateText(prompt);
            
            // Parse AI response to Biography object
            Biography biography = parseAiResponse(aiResponse, rawText);
            
            logger.info("Successfully parsed biography using AI for: {}", biography.getName());
            return biography;
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid input for biography parsing", e);
            throw e;
        } catch (Exception e) {
            logger.error("Error parsing biography with AI", e);
            throw new RuntimeException("Failed to parse biography with AI: " + e.getMessage(), e);
        }
    }

    private String buildExtractionPrompt(String rawText) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Extract structured biography information from the following free-form text. ");
        prompt.append("Return ONLY a valid JSON object with the following structure. ");
        prompt.append("Do not include any explanations or markdown formatting, only the JSON object.\n\n");
        
        prompt.append("Required JSON structure:\n");
        prompt.append("{\n");
        prompt.append("  \"personalInformation\": {\n");
        prompt.append("    \"firstName\": \"string\",\n");
        prompt.append("    \"lastName\": \"string\",\n");
        prompt.append("    \"email\": \"string\",\n");
        prompt.append("    \"phone\": \"string\",\n");
        prompt.append("    \"address\": {\n");
        prompt.append("      \"street\": \"string\",\n");
        prompt.append("      \"postalCode\": \"string\",\n");
        prompt.append("      \"city\": \"string\",\n");
        prompt.append("      \"country\": \"string\"\n");
        prompt.append("    },\n");
        prompt.append("    \"birthDate\": \"YYYY-MM-DD\",\n");
        prompt.append("    \"nationality\": \"string\"\n");
        prompt.append("  },\n");
        prompt.append("  \"education\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"degree\": \"string\",\n");
        prompt.append("      \"field\": \"string (optional)\",\n");
        prompt.append("      \"university\": \"string\",\n");
        prompt.append("      \"location\": \"string (optional)\",\n");
        prompt.append("      \"startDate\": \"YYYY-MM\",\n");
        prompt.append("      \"endDate\": \"YYYY-MM\",\n");
        prompt.append("      \"description\": \"string (optional)\"\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"workExperience\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"position\": \"string\",\n");
        prompt.append("      \"company\": \"string\",\n");
        prompt.append("      \"location\": \"string (optional)\",\n");
        prompt.append("      \"startDate\": \"YYYY-MM\",\n");
        prompt.append("      \"endDate\": \"YYYY-MM or 'present'\",\n");
        prompt.append("      \"description\": \"string\",\n");
        prompt.append("      \"technologies\": [\"string\"]\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"skills\": {\n");
        prompt.append("    \"programmingLanguages\": [\"string\"],\n");
        prompt.append("    \"frameworks\": [\"string\"],\n");
        prompt.append("    \"databases\": [\"string\"],\n");
        prompt.append("    \"tools\": [\"string\"],\n");
        prompt.append("    \"languages\": [\n");
        prompt.append("      {\n");
        prompt.append("        \"language\": \"string\",\n");
        prompt.append("        \"level\": \"string\"\n");
        prompt.append("      }\n");
        prompt.append("    ]\n");
        prompt.append("  },\n");
        prompt.append("  \"certifications\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"name\": \"string\",\n");
        prompt.append("      \"issuer\": \"string\",\n");
        prompt.append("      \"date\": \"YYYY-MM\"\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");
        
        prompt.append("Extract information from this text:\n");
        prompt.append(rawText);
        prompt.append("\n\n");
        prompt.append("Return only the JSON object, no other text.");
        
        return prompt.toString();
    }

    private Biography parseAiResponse(String aiResponse, String originalText) {
        try {
            // Try to extract JSON from response (AI might wrap it in markdown or add text)
            String jsonText = extractJsonFromResponse(aiResponse);
            
            // Parse JSON
            JsonObject jsonObject = gson.fromJson(jsonText, JsonObject.class);
            
            // Convert to Biography object
            Biography biography = convertJsonToBiography(jsonObject);
            
            // Validate that we got at least some basic information
            if (biography.getName() == null || biography.getName().trim().isEmpty()) {
                logger.warn("AI response did not contain name, attempting fallback extraction");
                // Try to extract name from original text as fallback
                String extractedName = extractNameFromText(originalText);
                if (extractedName != null && !extractedName.isEmpty()) {
                    biography.setName(extractedName);
                }
            }
            
            return biography;
            
        } catch (Exception e) {
            logger.error("Error parsing AI response: {}", aiResponse, e);
            throw new RuntimeException("Failed to parse AI response: " + e.getMessage(), e);
        }
    }

    private String extractJsonFromResponse(String response) {
        // Remove markdown code blocks if present
        String cleaned = response.trim();
        
        // Remove ```json or ``` markers
        if (cleaned.startsWith("```")) {
            int startIndex = cleaned.indexOf("\n");
            if (startIndex > 0) {
                cleaned = cleaned.substring(startIndex + 1);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
        }
        
        cleaned = cleaned.trim();
        
        // Find JSON object boundaries
        int startBrace = cleaned.indexOf("{");
        int endBrace = cleaned.lastIndexOf("}");
        
        if (startBrace >= 0 && endBrace > startBrace) {
            return cleaned.substring(startBrace, endBrace + 1);
        }
        
        return cleaned;
    }

    private Biography convertJsonToBiography(JsonObject jsonObject) {
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
                String endDate = workObj.has("endDate") ? 
                        workObj.get("endDate").getAsString() : "";
                // Handle "present" as end date
                if ("present".equalsIgnoreCase(endDate)) {
                    endDate = "present";
                }
                workExp.setEndDate(endDate);
                
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

    private String extractNameFromText(String text) {
        // Simple fallback: try to find name patterns
        // This is a basic fallback, AI should handle this better
        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.toLowerCase().startsWith("name:") || 
                line.toLowerCase().startsWith("ich heiße") ||
                line.toLowerCase().startsWith("mein name ist")) {
                String name = line.substring(line.indexOf(":") + 1).trim();
                if (!name.isEmpty()) {
                    return name;
                }
            }
        }
        return null;
    }
}

