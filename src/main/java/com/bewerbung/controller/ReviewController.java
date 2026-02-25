package com.bewerbung.controller;

import com.bewerbung.dto.ReviewRequestDto;
import com.bewerbung.model.ReviewEntry;
import com.bewerbung.service.ReviewStorageService;
import jakarta.servlet.http.HttpServletRequest;
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
    public ResponseEntity<ReviewEntry> createReview(HttpServletRequest request, @Valid @RequestBody ReviewRequestDto dto) {
        String sessionId = request.getSession(true).getId();
        ReviewEntry saved = reviewStorageService.saveReview(sessionId, dto.getReview(), dto.getUserInfo(), dto.getSource());
        return ResponseEntity.ok(saved);
    }
}

