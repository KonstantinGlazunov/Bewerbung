package com.bewerbung.service;

import com.bewerbung.entity.SessionDataEntity;
import com.bewerbung.entity.SessionReviewEntity;
import com.bewerbung.model.ReviewEntry;
import com.bewerbung.repository.SessionDataRepository;
import com.bewerbung.repository.SessionReviewRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Единое хранилище данных по сессиям: при наличии Oracle — в БД, иначе — в файлах data/{sessionId}/ и output/{sessionId}/.
 */
@Service
public class SessionStorageService {

    private static final Logger logger = LoggerFactory.getLogger(SessionStorageService.class);
    private static final String DATA_DIR = "data";
    private static final String OUTPUT_DIR = "output";

    @Autowired(required = false)
    private SessionDataRepository sessionDataRepository;

    @Autowired(required = false)
    private SessionReviewRepository sessionReviewRepository;

    public boolean isDatabaseStorage() {
        return sessionDataRepository != null;
    }

    @PostConstruct
    public void logStorageMode() {
        if (isDatabaseStorage()) {
            logger.info("Session storage: database (Oracle). Data persists after session end.");
        } else {
            logger.warn("Session storage: files (data/, output/). To store data in database and persist after session end, set ORACLE_JDBC_URL, ORACLE_USER, ORACLE_PASSWORD, TNS_ADMIN; profile 'oracle' will be auto-activated.");
        }
    }

    // --- vacancy ---
    public void setVacancy(String sessionId, String text) {
        if (sessionId == null || sessionId.isBlank()) return;
        if (isDatabaseStorage()) {
            SessionDataEntity e = getOrCreate(sessionId);
            e.setVacancyText(text);
            sessionDataRepository.save(e);
        } else {
            writeFile(sessionDir(sessionId, DATA_DIR), "vacancy.txt", text);
        }
    }

    public String getVacancy(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return "";
        if (isDatabaseStorage()) {
            return sessionDataRepository.findBySessionId(sessionId)
                    .map(SessionDataEntity::getVacancyText)
                    .orElse(null);
        }
        return readFile(sessionDir(sessionId, DATA_DIR), "vacancy.txt");
    }

    // --- cv ---
    public void setCv(String sessionId, String text) {
        if (sessionId == null || sessionId.isBlank()) return;
        if (isDatabaseStorage()) {
            SessionDataEntity e = getOrCreate(sessionId);
            e.setCvText(text);
            sessionDataRepository.save(e);
        } else {
            writeFile(sessionDir(sessionId, DATA_DIR), "cv.txt", text);
        }
    }

    public String getCv(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return "";
        if (isDatabaseStorage()) {
            return sessionDataRepository.findBySessionId(sessionId)
                    .map(SessionDataEntity::getCvText)
                    .orElse(null);
        }
        return readFile(sessionDir(sessionId, DATA_DIR), "cv.txt");
    }

    // --- state json ---
    public void setStateJson(String sessionId, String json) {
        if (sessionId == null || sessionId.isBlank()) return;
        if (isDatabaseStorage()) {
            SessionDataEntity e = getOrCreate(sessionId);
            e.setStateJson(json);
            sessionDataRepository.save(e);
        } else {
            writeFile(sessionDir(sessionId, DATA_DIR), "state.json", json);
        }
    }

    public String getStateJson(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;
        if (isDatabaseStorage()) {
            return sessionDataRepository.findBySessionId(sessionId)
                    .map(SessionDataEntity::getStateJson)
                    .orElse(null);
        }
        return readFile(sessionDir(sessionId, DATA_DIR), "state.json");
    }

    // --- anschreiben (data path and content) ---
    public void setAnschreibenTxt(String sessionId, String pathOrContent) {
        if (sessionId == null || sessionId.isBlank()) return;
        if (isDatabaseStorage()) {
            SessionDataEntity e = getOrCreate(sessionId);
            e.setAnschreibenTxt(pathOrContent);
            sessionDataRepository.save(e);
        } else {
            writeFile(sessionDir(sessionId, DATA_DIR), "anschreiben.txt", pathOrContent != null ? pathOrContent : "");
        }
    }

    /** Путь к файлу с копией Anschreiben (для чтения по этому пути) или логический ключ в БД. */
    public String getAnschreibenPath(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return "";
        if (isDatabaseStorage()) {
            return sessionDataRepository.findBySessionId(sessionId)
                    .map(SessionDataEntity::getAnschreibenTxt)
                    .orElse("");
        }
        String stateJson = getStateJson(sessionId);
        if (stateJson != null && !stateJson.isBlank()) {
            try {
                com.google.gson.JsonObject o = new com.google.gson.Gson().fromJson(stateJson, com.google.gson.JsonObject.class);
                if (o != null && o.has("anschreibenFile")) return o.get("anschreibenFile").getAsString();
            } catch (Exception ignored) { }
        }
        return sessionDir(sessionId, DATA_DIR).resolve("anschreiben.txt").toString();
    }

    /** Сохранить путь к файлу Anschreiben в state (для совместимости с логикой change detection). */
    public void setAnschreibenPath(String sessionId, String path) {
        if (sessionId == null || sessionId.isBlank()) return;
        if (isDatabaseStorage()) {
            SessionDataEntity e = getOrCreate(sessionId);
            e.setAnschreibenTxt(path);
            sessionDataRepository.save(e);
        } else {
            com.google.gson.JsonObject o = getStateJsonAsObject(sessionId);
            o.addProperty("anschreibenFile", path != null ? path : "");
            setStateJson(sessionId, o.toString());
        }
    }

    private com.google.gson.JsonObject getStateJsonAsObject(String sessionId) {
        String stateJson = getStateJson(sessionId);
        if (stateJson != null && !stateJson.isBlank()) {
            try {
                com.google.gson.JsonObject o = new com.google.gson.Gson().fromJson(stateJson, com.google.gson.JsonObject.class);
                if (o != null) return o;
            } catch (Exception ignored) { }
        }
        return new com.google.gson.JsonObject();
    }

    // --- output: anschreiben.md ---
    public void setAnschreibenMd(String sessionId, String content) {
        if (sessionId == null || sessionId.isBlank()) return;
        if (isDatabaseStorage()) {
            SessionDataEntity e = getOrCreate(sessionId);
            e.setAnschreibenMd(content);
            sessionDataRepository.save(e);
        } else {
            writeFile(sessionDir(sessionId, OUTPUT_DIR), "anschreiben.md", content);
        }
    }

    public String getAnschreibenMd(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;
        if (isDatabaseStorage()) {
            return sessionDataRepository.findBySessionId(sessionId)
                    .map(SessionDataEntity::getAnschreibenMd)
                    .orElse(null);
        }
        return readFile(sessionDir(sessionId, OUTPUT_DIR), "anschreiben.md");
    }

    // --- output: lebenslauf-filled.html ---
    public void setLebenslaufHtml(String sessionId, String content) {
        if (sessionId == null || sessionId.isBlank()) return;
        if (isDatabaseStorage()) {
            SessionDataEntity e = getOrCreate(sessionId);
            e.setLebenslaufHtml(content);
            sessionDataRepository.save(e);
        } else {
            writeFile(sessionDir(sessionId, OUTPUT_DIR), "lebenslauf-filled.html", content);
        }
    }

    public String getLebenslaufHtml(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;
        if (isDatabaseStorage()) {
            return sessionDataRepository.findBySessionId(sessionId)
                    .map(SessionDataEntity::getLebenslaufHtml)
                    .orElse(null);
        }
        return readFile(sessionDir(sessionId, OUTPUT_DIR), "lebenslauf-filled.html");
    }

    // --- output: notes.json ---
    public void setNotesJson(String sessionId, String json) {
        if (sessionId == null || sessionId.isBlank()) return;
        if (isDatabaseStorage()) {
            SessionDataEntity e = getOrCreate(sessionId);
            e.setNotesJson(json);
            sessionDataRepository.save(e);
        } else {
            writeFile(sessionDir(sessionId, OUTPUT_DIR), "notes.json", json);
        }
    }

    // --- analysis ---
    public void setAnalysisMd(String sessionId, String content) {
        if (sessionId == null || sessionId.isBlank()) return;
        if (isDatabaseStorage()) {
            SessionDataEntity e = getOrCreate(sessionId);
            e.setAnalysisMd(content);
            sessionDataRepository.save(e);
        } else {
            writeFile(sessionDir(sessionId, OUTPUT_DIR), "analysis.md", content);
        }
    }

    // --- photo ---
    public void setPhoto(String sessionId, byte[] bytes, String mimeType) {
        if (sessionId == null || sessionId.isBlank()) return;
        if (isDatabaseStorage()) {
            SessionDataEntity e = getOrCreate(sessionId);
            e.setPhotoBlob(bytes);
            e.setPhotoMime(mimeType);
            sessionDataRepository.save(e);
        } else {
            Path dir = sessionDir(sessionId, DATA_DIR);
            Path file = dir.resolve("photo.bin");
            try {
                Files.createDirectories(dir);
                Files.write(file, bytes != null ? bytes : new byte[0]);
                if (mimeType != null) {
                    Files.writeString(dir.resolve("photo.mime"), mimeType, StandardCharsets.UTF_8);
                }
            } catch (IOException ex) {
                logger.error("Failed to save session photo to file", ex);
                throw new RuntimeException("Failed to save session photo", ex);
            }
        }
    }

    public Optional<PhotoData> getPhoto(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return Optional.empty();
        if (isDatabaseStorage()) {
            return sessionDataRepository.findBySessionId(sessionId)
                    .filter(e -> e.getPhotoBlob() != null && e.getPhotoBlob().length > 0)
                    .map(e -> new PhotoData(e.getPhotoBlob(), e.getPhotoMime() != null ? e.getPhotoMime() : "image/jpeg"));
        }
        Path dir = sessionDir(sessionId, DATA_DIR);
        Path file = dir.resolve("photo.bin");
        if (!Files.exists(file)) return Optional.empty();
        try {
            byte[] bytes = Files.readAllBytes(file);
            String mime = "image/jpeg";
            Path mimeFile = dir.resolve("photo.mime");
            if (Files.exists(mimeFile)) {
                mime = Files.readString(mimeFile, StandardCharsets.UTF_8).trim();
            }
            return Optional.of(new PhotoData(bytes, mime));
        } catch (IOException e) {
            logger.warn("Failed to read session photo", e);
            return Optional.empty();
        }
    }

    public static final class PhotoData {
        private final byte[] bytes;
        private final String mimeType;

        public PhotoData(byte[] bytes, String mimeType) {
            this.bytes = bytes;
            this.mimeType = mimeType;
        }
        public byte[] getBytes() { return bytes; }
        public String getMimeType() { return mimeType; }
    }

    // --- reviews ---
    public void addReview(String sessionId, String reviewText, String source) {
        if (sessionId == null || sessionId.isBlank()) return;
        if (isDatabaseStorage()) {
            SessionReviewEntity e = new SessionReviewEntity();
            e.setSessionId(sessionId);
            e.setReviewText(reviewText);
            e.setSource(source);
            sessionReviewRepository.save(e);
        } else {
            List<ReviewEntry> list = getReviewsInternal(sessionId);
            list.add(new ReviewEntry(reviewText, Instant.now().toString(), source));
            writeReviewsFile(sessionId, list);
        }
    }

    public List<ReviewEntry> getReviews(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return new ArrayList<>();
        if (isDatabaseStorage()) {
            return sessionReviewRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                    .map(r -> new ReviewEntry(r.getReviewText(), r.getCreatedAt() != null ? r.getCreatedAt().toString() : "", r.getSource()))
                    .collect(Collectors.toList());
        }
        return getReviewsInternal(sessionId);
    }

    private List<ReviewEntry> getReviewsInternal(String sessionId) {
        String json = readFile(sessionDir(sessionId, DATA_DIR), "reviews.json");
        if (json == null || json.trim().isEmpty()) return new ArrayList<>();
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            com.google.gson.reflect.TypeToken<List<ReviewEntry>> type = new com.google.gson.reflect.TypeToken<List<ReviewEntry>>() {};
            List<ReviewEntry> list = gson.fromJson(json, type.getType());
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            logger.warn("Failed to parse reviews.json for session {}", sessionId, e);
            return new ArrayList<>();
        }
    }

    private void writeReviewsFile(String sessionId, List<ReviewEntry> list) {
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(list);
        writeFile(sessionDir(sessionId, DATA_DIR), "reviews.json", json);
    }

    private SessionDataEntity getOrCreate(String sessionId) {
        return sessionDataRepository.findBySessionId(sessionId)
                .orElseGet(() -> {
                    SessionDataEntity e = new SessionDataEntity();
                    e.setSessionId(sessionId);
                    return e;
                });
    }

    private Path sessionDir(String sessionId, String baseDir) {
        String safe = sessionId.replaceAll("[^A-Za-z0-9_-]", "_");
        return Paths.get(baseDir, safe);
    }

    private void writeFile(Path dir, String fileName, String content) {
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(fileName);
            Files.writeString(file, content != null ? content : "", StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to write {} for session", fileName, e);
            throw new RuntimeException("Failed to write session file: " + fileName, e);
        }
    }

    private String readFile(Path dir, String fileName) {
        Path file = dir.resolve(fileName);
        if (!Files.exists(file)) return null;
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("Failed to read {} for session", fileName, e);
            return null;
        }
    }
}
