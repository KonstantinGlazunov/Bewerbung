package com.bewerbung.service;

import com.bewerbung.model.JobRequirements;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

@Service
public class JobPostingAiAnalyzerService {

    private static final Logger logger = LoggerFactory.getLogger(JobPostingAiAnalyzerService.class);
    
    private final OpenAiService openAiService;
    private final Gson gson;

    @Autowired
    public JobPostingAiAnalyzerService(OpenAiService openAiService) {
        this.openAiService = openAiService;
        this.gson = new Gson();
    }

    public JobRequirements analyzeJobPosting(String jobPostingText) {
        logger.info("Analyzing job posting using AI");
        
        if (jobPostingText == null || jobPostingText.trim().isEmpty()) {
            throw new IllegalArgumentException("Job posting text must not be empty");
        }
        
        try {
            // Build prompt for AI to extract job requirements
            String prompt = buildExtractionPrompt(jobPostingText);
            
            // Get AI response using LIGHT model for analysis
            String aiResponse = openAiService.generateTextWithLightModel(prompt);
            
            // Parse AI response to JobRequirements object
            JobRequirements jobRequirements = parseAiResponse(aiResponse);
            
            logger.info("Successfully analyzed job posting for position: {}", jobRequirements.getPosition());
            return jobRequirements;
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid input for job posting analysis", e);
            throw e;
        } catch (Exception e) {
            logger.error("Error analyzing job posting with AI", e);
            throw new RuntimeException("Failed to analyze job posting with AI: " + e.getMessage(), e);
        }
    }

    private String buildExtractionPrompt(String jobPostingText) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Extract structured job requirements information from the following job posting text. ");
        prompt.append("Return ONLY a valid JSON object with the following structure. ");
        prompt.append("Do not include any explanations or markdown formatting, only the JSON object.\n\n");
        
        prompt.append("Required JSON structure:\n");
        prompt.append("{\n");
        prompt.append("  \"position\": \"string (job title/position name)\",\n");
        prompt.append("  \"company\": \"string (company name)\",\n");
        prompt.append("  \"location\": \"string (work location/city)\",\n");
        prompt.append("  \"education\": \"string (education requirements, e.g., degree requirements)\",\n");
        prompt.append("  \"experience\": \"string (required work experience)\",\n");
        prompt.append("  \"requiredSkills\": [\"string (list of required technical and professional skills)\"],\n");
        prompt.append("  \"preferredSkills\": [\"string (list of nice-to-have or preferred skills)\"],\n");
        prompt.append("  \"languages\": [\"string (required languages like German, English, etc.)\"]\n");
        prompt.append("}\n\n");
        
        prompt.append("Instructions:\n");
        prompt.append("- Extract the position/job title (e.g., 'Senior Software Developer', 'Java Developer')\n");
        prompt.append("- Identify the company name\n");
        prompt.append("- Find the work location/city\n");
        prompt.append("- Extract education requirements (e.g., 'Bachelor's degree in Computer Science')\n");
        prompt.append("- Extract experience requirements (e.g., '3+ years of Java development')\n");
        prompt.append("- List all required technical skills, frameworks, and tools\n");
        prompt.append("- Separately list preferred/nice-to-have skills\n");
        prompt.append("- Extract required language skills\n");
        prompt.append("- If any field is not mentioned, use an appropriate default value\n");
        prompt.append("- For arrays, return empty array [] if no information is available\n\n");
        
        prompt.append("Job Posting Text:\n");
        prompt.append(jobPostingText);
        prompt.append("\n\n");
        prompt.append("Return only the JSON object, no other text.");
        
        return prompt.toString();
    }

    private JobRequirements parseAiResponse(String aiResponse) {
        try {
            // Try to extract JSON from response (AI might wrap it in markdown or add text)
            String jsonText = extractJsonFromResponse(aiResponse);
            
            // Parse JSON
            JsonObject jsonObject = gson.fromJson(jsonText, JsonObject.class);
            
            // Convert to JobRequirements object
            JobRequirements jobRequirements = convertJsonToJobRequirements(jsonObject);
            
            // Validate that we got at least basic information
            if (jobRequirements.getPosition() == null || jobRequirements.getPosition().trim().isEmpty()) {
                logger.warn("AI response did not contain position, using default");
                jobRequirements.setPosition("Not specified");
            }
            
            if (jobRequirements.getCompany() == null || jobRequirements.getCompany().trim().isEmpty()) {
                logger.warn("AI response did not contain company, using default");
                jobRequirements.setCompany("Not specified");
            }
            
            return jobRequirements;
            
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

    private JobRequirements convertJsonToJobRequirements(JsonObject jsonObject) {
        JobRequirements jobRequirements = new JobRequirements();
        
        // Extract basic information
        jobRequirements.setPosition(jsonObject.has("position") ? 
                jsonObject.get("position").getAsString() : "Not specified");
        
        jobRequirements.setCompany(jsonObject.has("company") ? 
                jsonObject.get("company").getAsString() : "Not specified");
        
        jobRequirements.setLocation(jsonObject.has("location") ? 
                jsonObject.get("location").getAsString() : "Not specified");
        
        jobRequirements.setEducation(jsonObject.has("education") ? 
                jsonObject.get("education").getAsString() : "Not specified");
        
        jobRequirements.setExperience(jsonObject.has("experience") ? 
                jsonObject.get("experience").getAsString() : "Not specified");
        
        // Extract required skills
        List<String> requiredSkills = new ArrayList<>();
        if (jsonObject.has("requiredSkills")) {
            JsonArray skillsArray = jsonObject.getAsJsonArray("requiredSkills");
            if (skillsArray != null) {
                StreamSupport.stream(skillsArray.spliterator(), false)
                        .forEach(element -> requiredSkills.add(element.getAsString()));
            }
        }
        jobRequirements.setRequiredSkills(requiredSkills);
        
        // Extract preferred skills
        List<String> preferredSkills = new ArrayList<>();
        if (jsonObject.has("preferredSkills")) {
            JsonArray skillsArray = jsonObject.getAsJsonArray("preferredSkills");
            if (skillsArray != null) {
                StreamSupport.stream(skillsArray.spliterator(), false)
                        .forEach(element -> preferredSkills.add(element.getAsString()));
            }
        }
        jobRequirements.setPreferredSkills(preferredSkills);
        
        // Extract languages
        List<String> languages = new ArrayList<>();
        if (jsonObject.has("languages")) {
            JsonArray languagesArray = jsonObject.getAsJsonArray("languages");
            if (languagesArray != null) {
                StreamSupport.stream(languagesArray.spliterator(), false)
                        .forEach(element -> languages.add(element.getAsString()));
            }
        }
        jobRequirements.setLanguages(languages);
        
        return jobRequirements;
    }
}

