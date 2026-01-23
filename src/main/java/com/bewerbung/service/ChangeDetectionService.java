package com.bewerbung.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

@Service
public class ChangeDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(ChangeDetectionService.class);
    private static final String DATA_DIR = "data";
    private static final String VACANCY_FILE = "data/vacancy.txt";
    private static final String CV_FILE = "data/cv.txt";
    private static final String STATE_FILE = "data/state.json";
    private static final String ANSCHREIBEN_DATA_FILE = "data/anschreiben.txt";
    
    private final Gson gson;

    public ChangeDetectionService() {
        this.gson = new Gson();
        logger.info("ChangeDetectionService initialized. Working directory: {}", 
            System.getProperty("user.dir"));
        logger.info("Data directory will be: {}", Paths.get(DATA_DIR).toAbsolutePath());
        ensureDirectoriesExist();
    }

    private void ensureDirectoriesExist() {
        try {
            Files.createDirectories(Paths.get(DATA_DIR));
            Files.createDirectories(Paths.get("output"));
            
            // Initialize state.json if it doesn't exist
            Path statePath = Paths.get(STATE_FILE);
            if (!Files.exists(statePath)) {
                State initialState = new State();
                saveState(initialState);
                logger.info("Initialized state.json file");
            }
            
            // Create empty placeholder files if they don't exist
            Path vacancyPath = Paths.get(VACANCY_FILE);
            if (!Files.exists(vacancyPath)) {
                Files.write(vacancyPath, "".getBytes(StandardCharsets.UTF_8));
                logger.debug("Created placeholder vacancy.txt file");
            }
            
            Path cvPath = Paths.get(CV_FILE);
            if (!Files.exists(cvPath)) {
                Files.write(cvPath, "".getBytes(StandardCharsets.UTF_8));
                logger.debug("Created placeholder cv.txt file");
            }
        } catch (IOException e) {
            logger.error("Failed to create directories and files", e);
        }
    }

    public ChangeResult checkAndSave(String vacancyText, String cvText) {
        return checkAndSave(vacancyText, cvText, null);
    }

    public ChangeResult checkAndSave(String vacancyText, String cvText, String wishesText) {
        try {
            // Handle null inputs
            if (vacancyText == null) {
                vacancyText = "";
                logger.warn("Vacancy text is null, using empty string");
            }
            if (cvText == null) {
                cvText = "";
                logger.warn("CV text is null, using empty string");
            }
            if (wishesText == null) {
                wishesText = "";
            }

            logger.info("Checking changes - Vacancy length: {} chars, CV length: {} chars, Wishes length: {} chars", 
                vacancyText.length(), cvText.length(), wishesText.length());

            // Calculate hashes BEFORE saving (to compare with existing state)
            String vacancyHash = calculateHash(vacancyText);
            String cvHash = calculateHash(cvText);
            String wishesHash = calculateHash(wishesText);
            
            logger.info("Calculated hashes - Vacancy: {}..., CV: {}..., Wishes: {}...", 
                vacancyHash.substring(0, Math.min(8, vacancyHash.length())),
                cvHash.substring(0, Math.min(8, cvHash.length())),
                wishesHash.substring(0, Math.min(8, wishesHash.length())));

            // Load existing state
            State state = loadState();
            
            logger.info("Existing state - Vacancy hash: {}..., CV hash: {}..., Wishes hash: {}...",
                state.getVacancyHash().isEmpty() ? "(empty)" : state.getVacancyHash().substring(0, Math.min(8, state.getVacancyHash().length())),
                state.getCvHash().isEmpty() ? "(empty)" : state.getCvHash().substring(0, Math.min(8, state.getCvHash().length())),
                state.getWishesHash().isEmpty() ? "(empty)" : state.getWishesHash().substring(0, Math.min(8, state.getWishesHash().length())));

            // Check if hashes match
            boolean vacancyChanged = !vacancyHash.equals(state.getVacancyHash());
            boolean cvChanged = !cvHash.equals(state.getCvHash());
            boolean wishesChanged = !wishesHash.equals(state.getWishesHash());
            
            // Check if this is the first run (empty state) or if we have data to save
            boolean isFirstRun = state.getVacancyHash().isEmpty() && state.getCvHash().isEmpty();
            boolean hasDataToSave = (vacancyText != null && !vacancyText.trim().isEmpty()) || 
                                   (cvText != null && !cvText.trim().isEmpty());

            // Always save files if we have data (first run or changes detected)
            if (isFirstRun || vacancyChanged || cvChanged || wishesChanged || hasDataToSave) {
                if (isFirstRun) {
                    logger.info("First run detected - saving files. Vacancy length: {}, CV length: {}, Wishes length: {}", 
                        vacancyText != null ? vacancyText.length() : 0, 
                        cvText != null ? cvText.length() : 0,
                        wishesText != null ? wishesText.length() : 0);
                } else if (vacancyChanged || cvChanged || wishesChanged) {
                    logger.info("Changes detected - saving files. Vacancy changed: {}, CV changed: {}, Wishes changed: {}", 
                        vacancyChanged, cvChanged, wishesChanged);
                } else {
                    logger.info("Saving files with available data");
                }
                
                // Save files if we have data
                if (vacancyText != null && !vacancyText.trim().isEmpty()) {
                    saveTextToFile(VACANCY_FILE, vacancyText);
                } else {
                    logger.warn("Vacancy text is empty, skipping save");
                }
                
                if (cvText != null && !cvText.trim().isEmpty()) {
                    saveTextToFile(CV_FILE, cvText);
                } else {
                    logger.warn("CV text is empty, skipping save");
                }
            }

            // Only skip AI processing if no changes AND not first run
            if (!vacancyChanged && !cvChanged && !wishesChanged && !isFirstRun) {
                logger.info("No changes detected. Hashes match existing state. Skipping AI processing.");
                return new ChangeResult(false, false, false, false, "No changes detected");
            }

            // Update state with new hashes
            state.setVacancyHash(vacancyHash);
            state.setCvHash(cvHash);
            state.setWishesHash(wishesHash);
            state.setVacancyLastProcessed(vacancyChanged ? Instant.now().toString() : state.getVacancyLastProcessed());
            state.setCvLastProcessed(cvChanged ? Instant.now().toString() : state.getCvLastProcessed());
            saveState(state);
            
            logger.info("State updated with new hashes");

            String changeDescription = buildChangeDescription(vacancyChanged, cvChanged, wishesChanged);
            logger.info("Changes detected: {}", changeDescription);

            return new ChangeResult(true, vacancyChanged, cvChanged, wishesChanged, changeDescription);

        } catch (Exception e) {
            logger.error("Error in change detection", e);
            // On error, assume changes detected to ensure processing
            return new ChangeResult(true, true, true, true, "Error during change detection, processing anyway");
        }
    }

    private String buildChangeDescription(boolean vacancyChanged, boolean cvChanged, boolean wishesChanged) {
        StringBuilder desc = new StringBuilder();
        boolean first = true;
        
        if (vacancyChanged) {
            desc.append("Vacancy changed");
            first = false;
        }
        if (cvChanged) {
            if (!first) desc.append(", ");
            desc.append("CV changed");
            first = false;
        }
        if (wishesChanged) {
            if (!first) desc.append(", ");
            desc.append("Wishes changed");
            first = false;
        }
        
        if (first) {
            return "No changes detected";
        }
        return desc.toString();
    }

    private void saveTextToFile(String filePath, String content) throws IOException {
        if (content == null) {
            logger.warn("Content is null for file: {}, skipping save", filePath);
            return;
        }
        
        if (content.trim().isEmpty()) {
            logger.warn("Content is empty for file: {}, skipping save", filePath);
            return;
        }
        
        Path path = Paths.get(filePath).toAbsolutePath();
        logger.info("Saving {} bytes to: {}", content.length(), path);
        
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        logger.info("Successfully saved {} bytes to: {}", content.length(), path);
        
        // Verify file was written
        if (Files.exists(path)) {
            long fileSize = Files.size(path);
            logger.info("Verified file exists: {} ({} bytes)", path, fileSize);
            
            // Log first 100 chars for debugging
            if (fileSize > 0) {
                String preview = content.length() > 100 ? content.substring(0, 100) + "..." : content;
                logger.debug("File content preview: {}", preview.replace("\n", "\\n"));
            }
        } else {
            logger.error("File was not created: {}", path);
            throw new IOException("File was not created: " + path);
        }
    }

    private String calculateHash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("Hash calculation failed", e);
        }
    }

    private State loadState() {
        try {
            Path statePath = Paths.get(STATE_FILE).toAbsolutePath();
            logger.debug("Loading state from: {}", statePath);
            
            if (!Files.exists(statePath)) {
                logger.info("State file does not exist, using empty state");
                return new State();
            }
            
            String json = Files.readString(statePath, StandardCharsets.UTF_8);
            logger.debug("Loaded state JSON: {} bytes", json.length());
            
            JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
            
            State state = new State();
            if (jsonObject.has("vacancyHash")) {
                state.setVacancyHash(jsonObject.get("vacancyHash").getAsString());
            }
            if (jsonObject.has("cvHash")) {
                state.setCvHash(jsonObject.get("cvHash").getAsString());
            }
            if (jsonObject.has("vacancyLastProcessed")) {
                state.setVacancyLastProcessed(jsonObject.get("vacancyLastProcessed").getAsString());
            }
            if (jsonObject.has("cvLastProcessed")) {
                state.setCvLastProcessed(jsonObject.get("cvLastProcessed").getAsString());
            }
            if (jsonObject.has("wishesHash")) {
                state.setWishesHash(jsonObject.get("wishesHash").getAsString());
            }
            if (jsonObject.has("anschreibenFile")) {
                state.setAnschreibenFile(jsonObject.get("anschreibenFile").getAsString());
            }
            
            logger.debug("Loaded state - Vacancy hash length: {}, CV hash length: {}, Wishes hash length: {}", 
                state.getVacancyHash().length(), state.getCvHash().length(), state.getWishesHash().length());
            
            return state;
        } catch (Exception e) {
            logger.warn("Failed to load state, using empty state", e);
            return new State();
        }
    }

    private void saveState(State state) {
        try {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("vacancyHash", state.getVacancyHash());
            jsonObject.addProperty("cvHash", state.getCvHash());
            jsonObject.addProperty("wishesHash", state.getWishesHash());
            jsonObject.addProperty("vacancyLastProcessed", state.getVacancyLastProcessed());
            jsonObject.addProperty("cvLastProcessed", state.getCvLastProcessed());
            jsonObject.addProperty("anschreibenFile", state.getAnschreibenFile());
            
            String json = gson.toJson(jsonObject);
            Path statePath = Paths.get(STATE_FILE).toAbsolutePath();
            Files.write(statePath, json.getBytes(StandardCharsets.UTF_8));
            logger.info("State saved to: {} ({} bytes)", statePath, json.length());
        } catch (Exception e) {
            logger.error("Failed to save state", e);
        }
    }

    public static class ChangeResult {
        private final boolean hasChanges;
        private final boolean vacancyChanged;
        private final boolean cvChanged;
        private final boolean wishesChanged;
        private final String description;

        public ChangeResult(boolean hasChanges, boolean vacancyChanged, boolean cvChanged, boolean wishesChanged, String description) {
            this.hasChanges = hasChanges;
            this.vacancyChanged = vacancyChanged;
            this.cvChanged = cvChanged;
            this.wishesChanged = wishesChanged;
            this.description = description;
        }

        // Legacy constructor for backward compatibility
        public ChangeResult(boolean hasChanges, boolean vacancyChanged, boolean cvChanged, String description) {
            this(hasChanges, vacancyChanged, cvChanged, false, description);
        }

        public boolean hasChanges() {
            return hasChanges;
        }

        public boolean isVacancyChanged() {
            return vacancyChanged;
        }

        public boolean isCvChanged() {
            return cvChanged;
        }

        public boolean isWishesChanged() {
            return wishesChanged;
        }

        public String getDescription() {
            return description;
        }
    }

    private static class State {
        private String vacancyHash = "";
        private String cvHash = "";
        private String wishesHash = "";
        private String vacancyLastProcessed = "";
        private String cvLastProcessed = "";
        private String anschreibenFile = "";

        public String getVacancyHash() {
            return vacancyHash;
        }

        public void setVacancyHash(String vacancyHash) {
            this.vacancyHash = vacancyHash;
        }

        public String getCvHash() {
            return cvHash;
        }

        public void setCvHash(String cvHash) {
            this.cvHash = cvHash;
        }

        public String getWishesHash() {
            return wishesHash;
        }

        public void setWishesHash(String wishesHash) {
            this.wishesHash = wishesHash;
        }

        public String getVacancyLastProcessed() {
            return vacancyLastProcessed;
        }

        public void setVacancyLastProcessed(String vacancyLastProcessed) {
            this.vacancyLastProcessed = vacancyLastProcessed;
        }

        public String getCvLastProcessed() {
            return cvLastProcessed;
        }

        public void setCvLastProcessed(String cvLastProcessed) {
            this.cvLastProcessed = cvLastProcessed;
        }

        public String getAnschreibenFile() {
            return anschreibenFile;
        }

        public void setAnschreibenFile(String anschreibenFile) {
            this.anschreibenFile = anschreibenFile;
        }
    }
    
    public String getSavedAnschreibenPath() {
        State state = loadState();
        return state.getAnschreibenFile();
    }
    
    public void saveAnschreibenPath(String filePath) {
        State state = loadState();
        state.setAnschreibenFile(filePath);
        saveState(state);
    }
}

