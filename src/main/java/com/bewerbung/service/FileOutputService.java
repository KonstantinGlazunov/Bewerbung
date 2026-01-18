package com.bewerbung.service;

import com.bewerbung.model.JobRequirements;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;

@Service
public class FileOutputService {

    private static final Logger logger = LoggerFactory.getLogger(FileOutputService.class);
    private static final String OUTPUT_DIR = "output";
    private static final String ANALYSIS_FILE = "output/analysis.md";
    private static final String ANSCHREIBEN_FILE = "output/anschreiben.md";
    private static final String NOTES_FILE = "output/notes.json";
    
    private final Gson gson;

    public FileOutputService() {
        this.gson = new Gson();
        ensureDirectoriesExist();
    }

    private void ensureDirectoriesExist() {
        try {
            Files.createDirectories(Paths.get(OUTPUT_DIR));
        } catch (IOException e) {
            logger.error("Failed to create output directory", e);
        }
    }

    public void writeAnalysis(JobRequirements requirements) {
        try {
            StringBuilder analysis = new StringBuilder();
            analysis.append("# Job Requirements Analysis\n\n");
            analysis.append("**Position:** ").append(requirements.getPosition()).append("\n\n");
            analysis.append("**Company:** ").append(requirements.getCompany()).append("\n\n");
            analysis.append("**Location:** ").append(requirements.getLocation()).append("\n\n");
            analysis.append("**Education:** ").append(requirements.getEducation()).append("\n\n");
            analysis.append("**Experience:** ").append(requirements.getExperience()).append("\n\n");
            
            analysis.append("**Required Skills:**\n");
            for (String skill : requirements.getRequiredSkills()) {
                analysis.append("- ").append(skill).append("\n");
            }
            analysis.append("\n");
            
            analysis.append("**Preferred Skills:**\n");
            for (String skill : requirements.getPreferredSkills()) {
                analysis.append("- ").append(skill).append("\n");
            }
            analysis.append("\n");
            
            analysis.append("**Languages:**\n");
            for (String language : requirements.getLanguages()) {
                analysis.append("- ").append(language).append("\n");
            }
            
            Files.write(Paths.get(ANALYSIS_FILE), analysis.toString().getBytes(StandardCharsets.UTF_8));
            logger.info("Analysis written to: {}", ANALYSIS_FILE);
        } catch (IOException e) {
            logger.error("Failed to write analysis", e);
            throw new RuntimeException("Failed to write analysis", e);
        }
    }

    public void writeAnschreiben(String anschreiben) {
        writeAnschreiben(anschreiben, ANSCHREIBEN_FILE);
    }
    
    public void writeAnschreiben(String anschreiben, String filePath) {
        try {
            if (anschreiben == null || anschreiben.trim().isEmpty()) {
                logger.warn("Anschreiben is null or empty, skipping write");
                return;
            }
            
            java.nio.file.Path path = Paths.get(filePath).toAbsolutePath();
            logger.info("Writing anschreiben to: {} (length: {} chars)", path, anschreiben.length());
            
            // Ensure parent directory exists
            Files.createDirectories(path.getParent());
            
            Files.write(path, anschreiben.getBytes(StandardCharsets.UTF_8));
            
            // Verify file was written
            if (Files.exists(path)) {
                long fileSize = Files.size(path);
                logger.info("Anschreiben successfully written to: {} ({} bytes)", path, fileSize);
            } else {
                logger.error("File was not created: {}", path);
                throw new RuntimeException("File was not created: " + path);
            }
        } catch (IOException e) {
            logger.error("Failed to write anschreiben to: {}", filePath, e);
            throw new RuntimeException("Failed to write anschreiben", e);
        }
    }
    
    public String readAnschreiben(String filePath) {
        try {
            java.nio.file.Path path = Paths.get(filePath).toAbsolutePath();
            
            if (!Files.exists(path)) {
                logger.warn("Anschreiben file does not exist: {}", path);
                return null;
            }
            
            String content = Files.readString(path, StandardCharsets.UTF_8);
            logger.info("Successfully read anschreiben from: {} ({} chars)", path, content.length());
            return content;
        } catch (IOException e) {
            logger.error("Failed to read anschreiben from: {}", filePath, e);
            return null;
        }
    }

    public void writeNotes(String changeDescription, boolean vacancyChanged, boolean cvChanged) {
        try {
            JsonObject notes = new JsonObject();
            notes.addProperty("lastProcessed", Instant.now().toString());
            notes.addProperty("changeDescription", changeDescription);
            notes.addProperty("vacancyChanged", vacancyChanged);
            notes.addProperty("cvChanged", cvChanged);
            notes.addProperty("analysisFile", ANALYSIS_FILE);
            notes.addProperty("anschreibenFile", ANSCHREIBEN_FILE);
            
            String json = gson.toJson(notes);
            Files.write(Paths.get(NOTES_FILE), json.getBytes(StandardCharsets.UTF_8));
            logger.info("Notes written to: {}", NOTES_FILE);
        } catch (IOException e) {
            logger.error("Failed to write notes", e);
            throw new RuntimeException("Failed to write notes", e);
        }
    }
    
    public String loadSampleCoverLetter() {
        try {
            ClassPathResource resource = new ClassPathResource("static/sample_coverLetter.txt");
            if (!resource.exists()) {
                logger.warn("Sample cover letter file not found: static/sample_coverLetter.txt");
                return null;
            }
            
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            logger.info("Loaded sample cover letter ({} chars)", content.length());
            return content;
        } catch (IOException e) {
            logger.error("Failed to load sample cover letter", e);
            return null;
        }
    }
    
    public String loadDefaultJobPosting() {
        try {
            ClassPathResource resource = new ClassPathResource("static/sample_job_posting.txt");
            if (!resource.exists()) {
                logger.warn("Default job posting file not found: static/sample_job_posting.txt");
                return null;
            }
            
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            logger.debug("Loaded default job posting ({} chars)", content.length());
            return content.trim();
        } catch (IOException e) {
            logger.error("Failed to load default job posting", e);
            return null;
        }
    }
    
    public String loadDefaultBiography() {
        // Try JSON format first
        try {
            ClassPathResource resource = new ClassPathResource("input/biography.json");
            if (resource.exists()) {
                String content = resource.getContentAsString(StandardCharsets.UTF_8);
                logger.debug("Loaded default biography JSON ({} chars)", content.length());
                return content.trim();
            }
        } catch (IOException e) {
            logger.debug("Failed to load biography.json, trying text format", e);
        }
        
        // Try text format as fallback
        try {
            ClassPathResource resource = new ClassPathResource("static/sample_biography.txt");
            if (resource.exists()) {
                String content = resource.getContentAsString(StandardCharsets.UTF_8);
                logger.debug("Loaded default biography text ({} chars)", content.length());
                return content.trim();
            }
        } catch (IOException e) {
            logger.warn("Default biography file not found (neither JSON nor text format)");
        }
        
        return null;
    }
    
    public boolean isDefaultData(String vacancyText, String cvText) {
        String defaultJobPosting = loadDefaultJobPosting();
        String defaultBiography = loadDefaultBiography();
        
        if (defaultJobPosting == null || defaultBiography == null) {
            logger.warn("Cannot compare with defaults - default files not found");
            return false;
        }
        
        // Normalize vacancy text for comparison (trim whitespace)
        boolean vacancyMatches = vacancyText != null && vacancyText.trim().equals(defaultJobPosting);
        
        // Normalize biography for comparison (handle both JSON and text formats)
        boolean cvMatches = false;
        if (cvText != null && !cvText.trim().isEmpty()) {
            try {
                // Try to parse both as JSON
                JsonObject defaultJson = gson.fromJson(defaultBiography, JsonObject.class);
                JsonObject incomingJson = gson.fromJson(cvText, JsonObject.class);
                
                // Compare normalized JSON strings
                String normalizedDefault = gson.toJson(defaultJson);
                String normalizedIncoming = gson.toJson(incomingJson);
                cvMatches = normalizedDefault.equals(normalizedIncoming);
            } catch (Exception e) {
                // If JSON parsing fails, compare as plain text (for text format biography)
                logger.debug("Biography not in JSON format, using text comparison");
                cvMatches = cvText.trim().equals(defaultBiography);
            }
        }
        
        boolean isDefault = vacancyMatches && cvMatches;
        
        if (isDefault) {
            logger.info("Data matches default samples - will use sample cover letter without AI");
        } else {
            logger.debug("Data does not match defaults - vacancy match: {}, cv match: {}", vacancyMatches, cvMatches);
        }
        
        return isDefault;
    }
}

