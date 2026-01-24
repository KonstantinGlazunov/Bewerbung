package com.bewerbung.service;

import com.bewerbung.model.ReviewEntry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReviewStorageService {
    private static final Logger logger = LoggerFactory.getLogger(ReviewStorageService.class);
    private static final Path REVIEWS_PATH = Paths.get("data/reviews.json");
    private static final Type REVIEW_LIST_TYPE = new TypeToken<List<ReviewEntry>>() {}.getType();

    private final Gson gson;
    private final Object fileLock = new Object();

    public ReviewStorageService() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public ReviewEntry saveReview(String review) {
        String trimmedReview = review == null ? "" : review.trim();
        if (trimmedReview.isEmpty()) {
            throw new IllegalArgumentException("Review must not be blank");
        }
        ReviewEntry entry = new ReviewEntry(trimmedReview, Instant.now().toString());

        synchronized (fileLock) {
            ensureFileExists();
            List<ReviewEntry> reviews = readReviewsInternal();
            reviews.add(entry);
            writeReviewsInternal(reviews);
        }

        return entry;
    }

    private void ensureFileExists() {
        try {
            Files.createDirectories(REVIEWS_PATH.getParent());
            if (!Files.exists(REVIEWS_PATH)) {
                Files.writeString(REVIEWS_PATH, "[]", StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            logger.error("Failed to initialize reviews file", e);
            throw new RuntimeException("Failed to initialize reviews storage", e);
        }
    }

    private List<ReviewEntry> readReviewsInternal() {
        try {
            if (!Files.exists(REVIEWS_PATH)) {
                return new ArrayList<>();
            }
            String content = Files.readString(REVIEWS_PATH, StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) {
                return new ArrayList<>();
            }
            List<ReviewEntry> reviews = gson.fromJson(content, REVIEW_LIST_TYPE);
            return reviews != null ? reviews : new ArrayList<>();
        } catch (Exception e) {
            logger.warn("Failed to read reviews file, starting with empty list", e);
            return new ArrayList<>();
        }
    }

    private void writeReviewsInternal(List<ReviewEntry> reviews) {
        try {
            String json = gson.toJson(reviews, REVIEW_LIST_TYPE);
            Path tempFile = Files.createTempFile(REVIEWS_PATH.getParent(), "reviews", ".json");
            Files.writeString(tempFile, json, StandardCharsets.UTF_8);
            try {
                Files.move(tempFile, REVIEWS_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tempFile, REVIEWS_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.error("Failed to write reviews file", e);
            throw new RuntimeException("Failed to save review", e);
        }
    }
}

