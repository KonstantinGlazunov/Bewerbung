package com.bewerbung.service;

import com.bewerbung.model.JobRequirements;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        try {
            Files.write(Paths.get(ANSCHREIBEN_FILE), anschreiben.getBytes(StandardCharsets.UTF_8));
            logger.info("Anschreiben written to: {}", ANSCHREIBEN_FILE);
        } catch (IOException e) {
            logger.error("Failed to write anschreiben", e);
            throw new RuntimeException("Failed to write anschreiben", e);
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
}

