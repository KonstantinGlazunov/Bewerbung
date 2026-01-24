package com.bewerbung.controller;

import com.bewerbung.dto.ReviewRequestDto;
import com.bewerbung.model.ReviewEntry;
import com.bewerbung.service.ReviewStorageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final ReviewStorageService reviewStorageService;

    public ReviewController(ReviewStorageService reviewStorageService) {
        this.reviewStorageService = reviewStorageService;
    }

    @PostMapping
    public ResponseEntity<ReviewEntry> createReview(@Valid @RequestBody ReviewRequestDto request) {
        ReviewEntry saved = reviewStorageService.saveReview(request.getReview());
        return ResponseEntity.ok(saved);
    }
}

