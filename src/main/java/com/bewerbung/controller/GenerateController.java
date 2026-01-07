package com.bewerbung.controller;

import com.bewerbung.dto.CoverLetterResponseDto;
import com.bewerbung.dto.CvResponseDto;
import com.bewerbung.dto.GenerateRequestDto;
import com.bewerbung.dto.GenerateResponseDto;
import com.bewerbung.model.Biography;
import com.bewerbung.model.JobRequirements;
import com.bewerbung.service.AnschreibenGeneratorService;
import com.bewerbung.service.BiographyAiAnalyzerService;
import com.bewerbung.service.BiographyFileAnalyzerService;
import com.bewerbung.service.BiographyService;
import com.bewerbung.service.LebenslaufGeneratorService;
import com.bewerbung.service.VacancyAnalyzerService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/generate")
public class GenerateController {

    private static final Logger logger = LoggerFactory.getLogger(GenerateController.class);

    private final VacancyAnalyzerService vacancyAnalyzerService;
    private final BiographyService biographyService;
    private final BiographyFileAnalyzerService biographyFileAnalyzerService;
    private final BiographyAiAnalyzerService biographyAiAnalyzerService;
    private final AnschreibenGeneratorService anschreibenGeneratorService;
    private final LebenslaufGeneratorService lebenslaufGeneratorService;
    private final Gson gson;

    @Autowired
    public GenerateController(VacancyAnalyzerService vacancyAnalyzerService,
                             BiographyService biographyService,
                             BiographyFileAnalyzerService biographyFileAnalyzerService,
                             BiographyAiAnalyzerService biographyAiAnalyzerService,
                             AnschreibenGeneratorService anschreibenGeneratorService,
                             LebenslaufGeneratorService lebenslaufGeneratorService) {
        this.vacancyAnalyzerService = vacancyAnalyzerService;
        this.biographyService = biographyService;
        this.biographyFileAnalyzerService = biographyFileAnalyzerService;
        this.biographyAiAnalyzerService = biographyAiAnalyzerService;
        this.anschreibenGeneratorService = anschreibenGeneratorService;
        this.lebenslaufGeneratorService = lebenslaufGeneratorService;
        this.gson = new Gson();
    }

    @PostMapping
    public ResponseEntity<GenerateResponseDto> generate(@Valid @RequestBody GenerateRequestDto request) {
        logger.info("Received generate request");
        
        // Validate biography is not empty (null is already handled by @NotNull)
        if (request.getBiography().isEmpty()) {
            throw new IllegalArgumentException("Biography must not be empty");
        }

        // Convert biography Map to JsonObject
        String biographyJsonString = gson.toJson(request.getBiography());
        JsonObject biographyJson = gson.fromJson(biographyJsonString, JsonObject.class);
        Biography biography = biographyService.parseBiographyFromJson(biographyJson);

        // Analyze job posting
        JobRequirements jobRequirements = vacancyAnalyzerService.analyzeVacancy(request.getJobPosting());

        // Generate cover letter (Anschreiben)
        String candidateName = biography.getName();
        String coverLetter = anschreibenGeneratorService.generateAnschreiben(jobRequirements, candidateName);

        // Generate CV (Lebenslauf)
        String cv = lebenslaufGeneratorService.generateLebenslauf(biography);

        // Build response
        GenerateResponseDto response = new GenerateResponseDto(coverLetter, cv);
        
        logger.info("Successfully generated cover letter and CV");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cover-letter")
    public ResponseEntity<CoverLetterResponseDto> generateCoverLetter(@Valid @RequestBody GenerateRequestDto request) {
        logger.info("Received cover letter generation request");
        
        // Validate biography is not empty (null is already handled by @NotNull)
        if (request.getBiography().isEmpty()) {
            throw new IllegalArgumentException("Biography must not be empty");
        }

        // Convert biography Map to JsonObject
        String biographyJsonString = gson.toJson(request.getBiography());
        JsonObject biographyJson = gson.fromJson(biographyJsonString, JsonObject.class);
        Biography biography = biographyService.parseBiographyFromJson(biographyJson);

        // Analyze job posting
        JobRequirements jobRequirements = vacancyAnalyzerService.analyzeVacancy(request.getJobPosting());

        // Generate cover letter (Anschreiben)
        String candidateName = biography.getName();
        String coverLetter = anschreibenGeneratorService.generateAnschreiben(jobRequirements, candidateName);

        // Build response
        CoverLetterResponseDto response = new CoverLetterResponseDto(coverLetter);
        
        logger.info("Successfully generated cover letter");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cv")
    public ResponseEntity<CvResponseDto> generateCv(@Valid @RequestBody GenerateRequestDto request) {
        logger.info("Received CV generation request");
        
        // Validate biography is not empty (null is already handled by @NotNull)
        if (request.getBiography().isEmpty()) {
            throw new IllegalArgumentException("Biography must not be empty");
        }

        // Convert biography Map to JsonObject
        String biographyJsonString = gson.toJson(request.getBiography());
        JsonObject biographyJson = gson.fromJson(biographyJsonString, JsonObject.class);
        Biography biography = biographyService.parseBiographyFromJson(biographyJson);

        // Generate CV (Lebenslauf)
        String cv = lebenslaufGeneratorService.generateLebenslauf(biography);

        // Build response
        CvResponseDto response = new CvResponseDto(cv);
        
        logger.info("Successfully generated CV");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/from-file")
    public ResponseEntity<GenerateResponseDto> generateFromFile(
            @RequestParam("biographyFile") MultipartFile biographyFile,
            @RequestParam("jobPosting") String jobPosting) {
        logger.info("Received generate request from file");
        
        // Validate inputs
        if (biographyFile == null || biographyFile.isEmpty()) {
            throw new IllegalArgumentException("Biography file must not be empty");
        }
        
        if (jobPosting == null || jobPosting.trim().isEmpty()) {
            throw new IllegalArgumentException("Job posting must not be blank");
        }

        // Read file content and parse using AI
        Biography biography;
        try {
            String biographyText = new String(biographyFile.getBytes(), StandardCharsets.UTF_8);
            // Use AI parser for free-form text
            biography = biographyAiAnalyzerService.parseBiography(biographyText);
        } catch (Exception e) {
            logger.error("Error parsing biography with AI, falling back to file parser", e);
            // Fallback to structured file parser if AI parsing fails
            biography = biographyFileAnalyzerService.parseBiographyFromFile(biographyFile);
        }

        // Analyze job posting
        JobRequirements jobRequirements = vacancyAnalyzerService.analyzeVacancy(jobPosting);

        // Generate cover letter (Anschreiben)
        String candidateName = biography.getName();
        String coverLetter = anschreibenGeneratorService.generateAnschreiben(jobRequirements, candidateName);

        // Generate CV (Lebenslauf)
        String cv = lebenslaufGeneratorService.generateLebenslauf(biography);

        // Build response
        GenerateResponseDto response = new GenerateResponseDto(coverLetter, cv);
        
        logger.info("Successfully generated cover letter and CV from file");
        return ResponseEntity.ok(response);
    }
}

