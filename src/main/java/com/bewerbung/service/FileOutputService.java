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
        return loadSampleCoverLetter("de");
    }
    
    public String loadSampleCoverLetter(String language) {
        // Normalize language
        if (language == null || language.trim().isEmpty()) {
            language = "de";
        }
        language = language.trim().toLowerCase();
        
        // Determine filename based on language
        String filename;
        if ("ru".equals(language)) {
            filename = "static/sample_coverLetter_ru.txt";
        } else if ("en".equals(language)) {
            filename = "static/sample_coverLetter_en.txt";
        } else {
            filename = "static/sample_coverLetter.txt"; // Default German
        }
        
        try {
            ClassPathResource resource = new ClassPathResource(filename);
            if (!resource.exists()) {
                logger.warn("Sample cover letter file not found: {}. Falling back to default.", filename);
                // Fallback to default German file
                if (!"de".equals(language)) {
                    resource = new ClassPathResource("static/sample_coverLetter.txt");
                    if (!resource.exists()) {
                        logger.warn("Default sample cover letter file also not found");
                        return null;
                    }
                } else {
                    return null;
                }
            }
            
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            logger.info("Loaded sample cover letter for language '{}' from {} ({} chars)", language, filename, content.length());
            return content;
        } catch (IOException e) {
            logger.error("Failed to load sample cover letter from {}", filename, e);
            return null;
        }
    }
    
    public String loadDefaultJobPosting() {
        return loadDefaultJobPosting("de");
    }
    
    public String loadDefaultJobPosting(String language) {
        // Normalize language
        if (language == null || language.trim().isEmpty()) {
            language = "de";
        }
        language = language.trim().toLowerCase();
        
        // Determine filename based on language
        String filename;
        if ("ru".equals(language)) {
            filename = "static/sample_job_posting_ru.txt";
        } else if ("en".equals(language)) {
            filename = "static/sample_job_posting_en.txt";
        } else {
            filename = "static/sample_job_posting.txt"; // Default German
        }
        
        try {
            ClassPathResource resource = new ClassPathResource(filename);
            if (!resource.exists()) {
                logger.warn("Default job posting file not found: {}. Falling back to default.", filename);
                // Fallback to default German file
                if (!"de".equals(language)) {
                    resource = new ClassPathResource("static/sample_job_posting.txt");
                    if (!resource.exists()) {
                        return null;
                    }
                } else {
                    return null;
                }
            }
            
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            logger.debug("Loaded default job posting for language '{}' from {} ({} chars)", language, filename, content.length());
            // Normalize line endings and trim
            return normalizeForComparison(content);
        } catch (IOException e) {
            logger.error("Failed to load default job posting for language '{}'", language, e);
            return null;
        }
    }
    
    public String loadDefaultBiography() {
        return loadDefaultBiography("de");
    }
    
    public String loadDefaultBiography(String language) {
        // Normalize language
        if (language == null || language.trim().isEmpty()) {
            language = "de";
        }
        language = language.trim().toLowerCase();
        
        // Try text format first (since we're using text format for samples)
        String filename;
        if ("ru".equals(language)) {
            filename = "static/sample_biography_ru.txt";
        } else if ("en".equals(language)) {
            filename = "static/sample_biography_en.txt";
        } else {
            filename = "static/sample_biography.txt"; // Default German
        }
        
        try {
            ClassPathResource resource = new ClassPathResource(filename);
            if (resource.exists()) {
                String content = resource.getContentAsString(StandardCharsets.UTF_8);
                logger.debug("Loaded default biography text for language '{}' from {} ({} chars)", language, filename, content.length());
                // Normalize line endings and trim
                return normalizeForComparison(content);
            }
        } catch (IOException e) {
            logger.debug("Failed to load biography for language '{}', trying fallback", language, e);
        }
        
        // Fallback to default German file if language-specific file not found
        if (!"de".equals(language)) {
            try {
                ClassPathResource resource = new ClassPathResource("static/sample_biography.txt");
                if (resource.exists()) {
                    String content = resource.getContentAsString(StandardCharsets.UTF_8);
                    logger.debug("Loaded default biography text (fallback) ({} chars)", content.length());
                    // Normalize line endings and trim
                    return normalizeForComparison(content);
                }
            } catch (IOException e) {
                logger.debug("Failed to load fallback biography", e);
            }
        }
        
        // Try JSON format as last resort
        try {
            ClassPathResource resource = new ClassPathResource("input/biography.json");
            if (resource.exists()) {
                String content = resource.getContentAsString(StandardCharsets.UTF_8);
                logger.debug("Loaded default biography JSON ({} chars)", content.length());
                // Normalize line endings and trim
                return normalizeForComparison(content);
            }
        } catch (IOException e) {
            logger.debug("Failed to load biography.json", e);
        }
        
        logger.warn("Default biography file not found for language '{}'", language);
        return null;
    }
    
    public boolean isDefaultData(String vacancyText, String cvText) {
        return isDefaultData(vacancyText, cvText, "de");
    }
    
    /**
     * Normalizes a string for comparison by:
     * - Trimming leading/trailing whitespace
     * - Normalizing line endings (CRLF -> LF)
     * - Removing trailing newlines at the end
     */
    private String normalizeForComparison(String text) {
        if (text == null) {
            return "";
        }
        // Normalize line endings (CRLF -> LF, CR -> LF)
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        // Trim leading and trailing whitespace
        normalized = normalized.trim();
        return normalized;
    }
    
    public boolean isDefaultData(String vacancyText, String cvText, String language) {
        String defaultJobPosting = loadDefaultJobPosting(language);
        String defaultBiography = loadDefaultBiography(language);
        
        if (defaultJobPosting == null || defaultBiography == null) {
            logger.warn("Cannot compare with defaults - default files not found for language '{}'", language);
            return false;
        }
        
        // Normalize both strings for comparison
        String normalizedVacancy = normalizeForComparison(vacancyText);
        String normalizedDefaultJobPosting = normalizeForComparison(defaultJobPosting);
        String normalizedCv = normalizeForComparison(cvText);
        String normalizedDefaultBiography = normalizeForComparison(defaultBiography);
        
        // Compare vacancy text
        boolean vacancyMatches = normalizedVacancy.equals(normalizedDefaultJobPosting);
        
        if (!vacancyMatches) {
            // Log detailed comparison for debugging
            logger.info("Vacancy text mismatch for language '{}':", language);
            logger.info("  Incoming length: {}, Default length: {}", 
                normalizedVacancy.length(), normalizedDefaultJobPosting.length());
            if (normalizedVacancy.length() != normalizedDefaultJobPosting.length()) {
                logger.info("  Lengths differ - this is why comparison failed");
            } else {
                // Find first difference
                int firstDiff = findFirstDifference(normalizedVacancy, normalizedDefaultJobPosting);
                if (firstDiff >= 0) {
                    int start = Math.max(0, firstDiff - 20);
                    int end = Math.min(normalizedVacancy.length(), firstDiff + 20);
                    logger.info("  First difference at position {}: incoming='{}', default='{}'", 
                        firstDiff,
                        normalizedVacancy.substring(start, Math.min(end, normalizedVacancy.length())).replace("\n", "\\n"),
                        normalizedDefaultJobPosting.substring(start, Math.min(end, normalizedDefaultJobPosting.length())).replace("\n", "\\n"));
                }
            }
        }
        
        // Normalize biography for comparison (handle both JSON and text formats)
        boolean cvMatches = false;
        if (normalizedCv != null && !normalizedCv.isEmpty()) {
            try {
                // Try to parse both as JSON
                JsonObject defaultJson = gson.fromJson(normalizedDefaultBiography, JsonObject.class);
                JsonObject incomingJson = gson.fromJson(normalizedCv, JsonObject.class);
                
                // Compare normalized JSON strings
                String normalizedDefault = gson.toJson(defaultJson);
                String normalizedIncoming = gson.toJson(incomingJson);
                cvMatches = normalizedDefault.equals(normalizedIncoming);
            } catch (Exception e) {
                // If JSON parsing fails, compare as plain text (for text format biography)
                logger.debug("Biography not in JSON format, using text comparison");
                cvMatches = normalizedCv.equals(normalizedDefaultBiography);
                
                if (!cvMatches) {
                    // Log detailed comparison for debugging
                    logger.info("Biography text mismatch for language '{}':", language);
                    logger.info("  Incoming length: {}, Default length: {}", 
                        normalizedCv.length(), normalizedDefaultBiography.length());
                    if (normalizedCv.length() != normalizedDefaultBiography.length()) {
                        logger.info("  Lengths differ - this is why comparison failed");
                    } else {
                        // Find first difference
                        int firstDiff = findFirstDifference(normalizedCv, normalizedDefaultBiography);
                        if (firstDiff >= 0) {
                            int start = Math.max(0, firstDiff - 20);
                            int end = Math.min(normalizedCv.length(), firstDiff + 20);
                            logger.info("  First difference at position {}: incoming='{}', default='{}'", 
                                firstDiff,
                                normalizedCv.substring(start, Math.min(end, normalizedCv.length())).replace("\n", "\\n"),
                                normalizedDefaultBiography.substring(start, Math.min(end, normalizedDefaultBiography.length())).replace("\n", "\\n"));
                        }
                    }
                }
            }
        }
        
        boolean isDefault = vacancyMatches && cvMatches;
        
        if (isDefault) {
            logger.info("Data matches default samples for language '{}' - will use sample cover letter without AI", language);
        } else {
            logger.info("Data does not match defaults for language '{}' - vacancy match: {}, cv match: {}", language, vacancyMatches, cvMatches);
        }
        
        return isDefault;
    }
    
    /**
     * Finds the first position where two strings differ
     */
    private int findFirstDifference(String s1, String s2) {
        int minLength = Math.min(s1.length(), s2.length());
        for (int i = 0; i < minLength; i++) {
            if (s1.charAt(i) != s2.charAt(i)) {
                return i;
            }
        }
        if (s1.length() != s2.length()) {
            return minLength;
        }
        return -1; // Strings are identical
    }
}

