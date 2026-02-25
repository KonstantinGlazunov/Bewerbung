package com.bewerbung.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class TempPhotoStorageService {

    private static final Logger logger = LoggerFactory.getLogger(TempPhotoStorageService.class);
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    private final SessionStorageService sessionStorage;

    public TempPhotoStorageService(SessionStorageService sessionStorage) {
        this.sessionStorage = sessionStorage;
    }

    public String saveTemporaryPhoto(String sessionId, MultipartFile photo) {
        validatePhoto(photo);
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session ID is required for photo upload");
        }
        try {
            byte[] bytes = photo.getInputStream().readAllBytes();
            String mimeType = normalizeMimeType(photo.getContentType());
            sessionStorage.setPhoto(sessionId, bytes, mimeType);
            String fileName = "cv-photo-" + UUID.randomUUID() + resolveExtension(photo.getOriginalFilename());
            logger.info("CV photo saved for session");
            return fileName;
        } catch (IOException e) {
            logger.error("Failed to store temporary CV photo", e);
            throw new RuntimeException("Failed to store uploaded photo", e);
        }
    }

    public Optional<String> getCurrentPhotoDataUri(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return sessionStorage.getPhoto(sessionId)
                .map(photo -> {
                    String encoded = Base64.getEncoder().encodeToString(photo.getBytes());
                    String mime = photo.getMimeType() != null ? photo.getMimeType() : "image/jpeg";
                    return "data:" + mime + ";base64," + encoded;
                });
    }

    private void validatePhoto(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            throw new IllegalArgumentException("Photo file must not be empty");
        }
        if (photo.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Photo file is too large (max 5 MB)");
        }
        String contentType = normalizeMimeType(photo.getContentType());
        if (!contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed");
        }
    }

    private String normalizeMimeType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "image/jpeg";
        }
        return contentType.toLowerCase(Locale.ROOT);
    }

    private String resolveExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return ".jpg";
        }
        String lower = originalFilename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return ".png";
        if (lower.endsWith(".gif")) return ".gif";
        if (lower.endsWith(".webp")) return ".webp";
        if (lower.endsWith(".jpeg")) return ".jpeg";
        if (lower.endsWith(".jpg")) return ".jpg";
        return ".jpg";
    }
}

