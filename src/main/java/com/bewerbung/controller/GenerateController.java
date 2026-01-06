package com.bewerbung.controller;

import com.bewerbung.dto.GenerateRequestDto;
import com.bewerbung.dto.GenerateResponseDto;
import com.bewerbung.model.Biography;
import com.bewerbung.model.JobRequirements;
import com.bewerbung.service.AnschreibenGeneratorService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/generate")
public class GenerateController {

    private static final Logger logger = LoggerFactory.getLogger(GenerateController.class);

    private final VacancyAnalyzerService vacancyAnalyzerService;
    private final BiographyService biographyService;
    private final AnschreibenGeneratorService anschreibenGeneratorService;
    private final LebenslaufGeneratorService lebenslaufGeneratorService;
    private final Gson gson;

    @Autowired
    public GenerateController(VacancyAnalyzerService vacancyAnalyzerService,
                             BiographyService biographyService,
                             AnschreibenGeneratorService anschreibenGeneratorService,
                             LebenslaufGeneratorService lebenslaufGeneratorService) {
        this.vacancyAnalyzerService = vacancyAnalyzerService;
        this.biographyService = biographyService;
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
}

