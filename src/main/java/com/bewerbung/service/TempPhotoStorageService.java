package com.bewerbung.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class TempPhotoStorageService {

    private static final Logger logger = LoggerFactory.getLogger(TempPhotoStorageService.class);
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    private final Path tempDir;
    private volatile Path currentPhotoPath;
    private volatile String currentPhotoMimeType;

    public TempPhotoStorageService() {
        this.tempDir = Path.of(System.getProperty("java.io.tmpdir"), "bewerbung-ai", "photos");
    }

    public synchronized String saveTemporaryPhoto(MultipartFile photo) {
        validatePhoto(photo);

        try {
            Files.createDirectories(tempDir);
            cleanupCurrentPhoto();

            String extension = resolveExtension(photo.getOriginalFilename());
            String fileName = "cv-photo-" + UUID.randomUUID() + extension;
            Path targetPath = tempDir.resolve(fileName);

            Files.copy(photo.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            currentPhotoPath = targetPath;
            currentPhotoMimeType = normalizeMimeType(photo.getContentType());

            logger.info("Temporary CV photo uploaded: {}", targetPath);
            return fileName;
        } catch (IOException e) {
            logger.error("Failed to store temporary CV photo", e);
            throw new RuntimeException("Failed to store uploaded photo", e);
        }
    }

    public synchronized Optional<String> getCurrentPhotoDataUri() {
        if (currentPhotoPath == null || !Files.exists(currentPhotoPath)) {
            return Optional.empty();
        }

        try {
            byte[] bytes = Files.readAllBytes(currentPhotoPath);
            String mimeType = currentPhotoMimeType != null ? currentPhotoMimeType : "image/jpeg";
            String encoded = Base64.getEncoder().encodeToString(bytes);
            return Optional.of("data:" + mimeType + ";base64," + encoded);
        } catch (IOException e) {
            logger.warn("Failed to read temporary CV photo from {}", currentPhotoPath, e);
            return Optional.empty();
        }
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

    private void cleanupCurrentPhoto() {
        if (currentPhotoPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(currentPhotoPath);
        } catch (IOException e) {
            logger.warn("Failed to delete previous temporary CV photo: {}", currentPhotoPath, e);
        }
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

