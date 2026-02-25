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

    private final Gson gson;
    private final SessionStorageService sessionStorage;

    public ChangeDetectionService(SessionStorageService sessionStorage) {
        this.gson = new Gson();
        this.sessionStorage = sessionStorage;
        logger.info("ChangeDetectionService initialized (session-scoped)");
        ensureDirectoriesExist();
    }

    private void ensureDirectoriesExist() {
        try {
            Files.createDirectories(Paths.get(DATA_DIR));
            Files.createDirectories(Paths.get("output"));
        } catch (IOException e) {
            logger.error("Failed to create directories", e);
        }
    }

    public ChangeResult checkAndSave(String sessionId, String vacancyText, String cvText) {
        return checkAndSave(sessionId, vacancyText, cvText, null, null);
    }

    public ChangeResult checkAndSave(String sessionId, String vacancyText, String cvText, String wishesText) {
        return checkAndSave(sessionId, vacancyText, cvText, wishesText, null);
    }

    public ChangeResult checkAndSave(String sessionId, String vacancyText, String cvText, String wishesText, String language) {
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

            // Normalize language (default to "de" if null or empty)
            if (language == null || language.trim().isEmpty()) {
                language = "de";
            }
            language = language.trim().toLowerCase(); // Normalize to lowercase
            
            // Calculate hashes BEFORE saving (to compare with existing state)
            String vacancyHash = calculateHash(vacancyText);
            String cvHash = calculateHash(cvText);
            String wishesHash = calculateHash(wishesText);
            
            logger.info("Calculated hashes - Vacancy: {}..., CV: {}..., Wishes: {}..., Language: {}", 
                vacancyHash.substring(0, Math.min(8, vacancyHash.length())),
                cvHash.substring(0, Math.min(8, cvHash.length())),
                wishesHash.substring(0, Math.min(8, wishesHash.length())),
                language);

            // Load existing state for this session
            State state = loadState(sessionId);
            
            // Normalize state language (default to "de" if empty)
            String stateLanguage = state.getLanguage();
            if (stateLanguage == null || stateLanguage.trim().isEmpty()) {
                stateLanguage = "de";
            }
            stateLanguage = stateLanguage.trim().toLowerCase();
            
            logger.info("Existing state - Vacancy hash: {}..., CV hash: {}..., Wishes hash: {}..., Language: {}",
                state.getVacancyHash().isEmpty() ? "(empty)" : state.getVacancyHash().substring(0, Math.min(8, state.getVacancyHash().length())),
                state.getCvHash().isEmpty() ? "(empty)" : state.getCvHash().substring(0, Math.min(8, state.getCvHash().length())),
                state.getWishesHash().isEmpty() ? "(empty)" : state.getWishesHash().substring(0, Math.min(8, state.getWishesHash().length())),
                stateLanguage);

            // Check if hashes match
            boolean vacancyChanged = !vacancyHash.equals(state.getVacancyHash());
            boolean cvChanged = !cvHash.equals(state.getCvHash());
            boolean wishesChanged = !wishesHash.equals(state.getWishesHash());
            boolean languageChanged = !language.equals(stateLanguage);
            
            logger.info("Change detection - vacancyChanged: {}, cvChanged: {}, wishesChanged: {}, languageChanged: {} (current: '{}' vs state: '{}')",
                vacancyChanged, cvChanged, wishesChanged, languageChanged, language, stateLanguage);
            
            // Check if this is the first run (empty state) or if we have data to save
            boolean isFirstRun = state.getVacancyHash().isEmpty() && state.getCvHash().isEmpty();
            boolean hasDataToSave = (vacancyText != null && !vacancyText.trim().isEmpty()) || 
                                   (cvText != null && !cvText.trim().isEmpty());

            // Always save files if we have data (first run or changes detected)
            if (isFirstRun || vacancyChanged || cvChanged || wishesChanged || languageChanged || hasDataToSave) {
                if (isFirstRun) {
                    logger.info("First run detected - saving files. Vacancy length: {}, CV length: {}, Wishes length: {}", 
                        vacancyText != null ? vacancyText.length() : 0, 
                        cvText != null ? cvText.length() : 0,
                        wishesText != null ? wishesText.length() : 0);
                } else if (vacancyChanged || cvChanged || wishesChanged || languageChanged) {
                    logger.info("Changes detected - saving files. Vacancy changed: {}, CV changed: {}, Wishes changed: {}, Language changed: {}", 
                        vacancyChanged, cvChanged, wishesChanged, languageChanged);
                } else {
                    logger.info("Saving files with available data");
                }
                
                // Save to session storage if we have data
                if (vacancyText != null && !vacancyText.trim().isEmpty()) {
                    sessionStorage.setVacancy(sessionId, vacancyText);
                } else {
                    logger.warn("Vacancy text is empty, skipping save");
                }
                if (cvText != null && !cvText.trim().isEmpty()) {
                    sessionStorage.setCv(sessionId, cvText);
                } else {
                    logger.warn("CV text is empty, skipping save");
                }
            }

            // Only skip AI processing if no changes AND not first run
            if (!vacancyChanged && !cvChanged && !wishesChanged && !languageChanged && !isFirstRun) {
                logger.info("No changes detected. Hashes match existing state. Skipping AI processing.");
                return new ChangeResult(false, false, false, false, false, "No changes detected");
            }

            // Update state with new hashes and language
            state.setVacancyHash(vacancyHash);
            state.setCvHash(cvHash);
            state.setWishesHash(wishesHash);
            state.setLanguage(language);
            state.setVacancyLastProcessed(vacancyChanged ? Instant.now().toString() : state.getVacancyLastProcessed());
            state.setCvLastProcessed(cvChanged ? Instant.now().toString() : state.getCvLastProcessed());
            saveState(sessionId, state);
            
            logger.info("State updated with new hashes and language: {}", language);

            String changeDescription = buildChangeDescription(vacancyChanged, cvChanged, wishesChanged, languageChanged);
            logger.info("Changes detected: {}", changeDescription);

            return new ChangeResult(true, vacancyChanged, cvChanged, wishesChanged, languageChanged, changeDescription);

        } catch (Exception e) {
            logger.error("Error in change detection", e);
            // On error, assume changes detected to ensure processing
            return new ChangeResult(true, true, true, true, true, "Error during change detection, processing anyway");
        }
    }

    private String buildChangeDescription(boolean vacancyChanged, boolean cvChanged, boolean wishesChanged, boolean languageChanged) {
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
        if (languageChanged) {
            if (!first) desc.append(", ");
            desc.append("Language changed");
            first = false;
        }
        
        if (first) {
            return "No changes detected";
        }
        return desc.toString();
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

    private State loadState(String sessionId) {
        try {
            String json = sessionStorage.getStateJson(sessionId);
            if (json == null || json.isBlank()) {
                logger.debug("No state for session, using empty state");
                return new State();
            }
            logger.debug("Loaded state JSON: {} bytes for session", json.length());
            JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
            if (jsonObject == null) return new State();
            State state = new State();
            if (jsonObject.has("vacancyHash")) state.setVacancyHash(jsonObject.get("vacancyHash").getAsString());
            if (jsonObject.has("cvHash")) state.setCvHash(jsonObject.get("cvHash").getAsString());
            if (jsonObject.has("vacancyLastProcessed")) state.setVacancyLastProcessed(jsonObject.get("vacancyLastProcessed").getAsString());
            if (jsonObject.has("cvLastProcessed")) state.setCvLastProcessed(jsonObject.get("cvLastProcessed").getAsString());
            if (jsonObject.has("wishesHash")) state.setWishesHash(jsonObject.get("wishesHash").getAsString());
            if (jsonObject.has("language")) state.setLanguage(jsonObject.get("language").getAsString());
            if (jsonObject.has("anschreibenFile")) state.setAnschreibenFile(jsonObject.get("anschreibenFile").getAsString());
            return state;
        } catch (Exception e) {
            logger.warn("Failed to load state for session, using empty state", e);
            return new State();
        }
    }

    private void saveState(String sessionId, State state) {
        try {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("vacancyHash", state.getVacancyHash());
            jsonObject.addProperty("cvHash", state.getCvHash());
            jsonObject.addProperty("wishesHash", state.getWishesHash());
            jsonObject.addProperty("language", state.getLanguage());
            jsonObject.addProperty("vacancyLastProcessed", state.getVacancyLastProcessed());
            jsonObject.addProperty("cvLastProcessed", state.getCvLastProcessed());
            jsonObject.addProperty("anschreibenFile", state.getAnschreibenFile());
            String json = gson.toJson(jsonObject);
            sessionStorage.setStateJson(sessionId, json);
            logger.debug("State saved for session ({} bytes)", json.length());
        } catch (Exception e) {
            logger.error("Failed to save state", e);
        }
    }

    public static class ChangeResult {
        private final boolean hasChanges;
        private final boolean vacancyChanged;
        private final boolean cvChanged;
        private final boolean wishesChanged;
        private final boolean languageChanged;
        private final String description;

        public ChangeResult(boolean hasChanges, boolean vacancyChanged, boolean cvChanged, boolean wishesChanged, boolean languageChanged, String description) {
            this.hasChanges = hasChanges;
            this.vacancyChanged = vacancyChanged;
            this.cvChanged = cvChanged;
            this.wishesChanged = wishesChanged;
            this.languageChanged = languageChanged;
            this.description = description;
        }

        // Legacy constructor for backward compatibility
        public ChangeResult(boolean hasChanges, boolean vacancyChanged, boolean cvChanged, String description) {
            this(hasChanges, vacancyChanged, cvChanged, false, false, description);
        }

        // Legacy constructor for backward compatibility
        public ChangeResult(boolean hasChanges, boolean vacancyChanged, boolean cvChanged, boolean wishesChanged, String description) {
            this(hasChanges, vacancyChanged, cvChanged, wishesChanged, false, description);
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

        public boolean isLanguageChanged() {
            return languageChanged;
        }

        public String getDescription() {
            return description;
        }
    }

    private static class State {
        private String vacancyHash = "";
        private String cvHash = "";
        private String wishesHash = "";
        private String language = "de";
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

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language != null ? language : "de";
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
    
    public String getSavedAnschreibenPath(String sessionId) {
        return sessionStorage.getAnschreibenPath(sessionId);
    }

    public void saveAnschreibenPath(String sessionId, String filePath) {
        sessionStorage.setAnschreibenPath(sessionId, filePath);
    }
}

