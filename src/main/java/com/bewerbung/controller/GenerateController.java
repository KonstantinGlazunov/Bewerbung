package com.bewerbung.controller;

import com.bewerbung.dto.GenerateRequestDto;
import com.bewerbung.dto.GenerateResponseDto;
import com.bewerbung.dto.PdfRequestDto;
import com.bewerbung.model.Biography;
import com.bewerbung.model.JobRequirements;
import com.bewerbung.service.AnschreibenGeneratorService;
import com.bewerbung.service.BiographyAiAnalyzerService;
import com.bewerbung.service.BiographyFileAnalyzerService;
import com.bewerbung.service.BiographyService;
import com.bewerbung.service.ChangeDetectionService;
import com.bewerbung.service.FileOutputService;
import com.bewerbung.service.PdfGenerationService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/generate")
public class GenerateController {

    private static final Logger logger = LoggerFactory.getLogger(GenerateController.class);

    private final VacancyAnalyzerService vacancyAnalyzerService;
    private final BiographyService biographyService;
    private final BiographyFileAnalyzerService biographyFileAnalyzerService;
    private final BiographyAiAnalyzerService biographyAiAnalyzerService;
    private final AnschreibenGeneratorService anschreibenGeneratorService;
    private final ChangeDetectionService changeDetectionService;
    private final FileOutputService fileOutputService;
    private final PdfGenerationService pdfGenerationService;
    private final Gson gson;

    @Autowired
    public GenerateController(VacancyAnalyzerService vacancyAnalyzerService,
                             BiographyService biographyService,
                             BiographyFileAnalyzerService biographyFileAnalyzerService,
                             BiographyAiAnalyzerService biographyAiAnalyzerService,
                             AnschreibenGeneratorService anschreibenGeneratorService,
                             ChangeDetectionService changeDetectionService,
                             FileOutputService fileOutputService,
                             PdfGenerationService pdfGenerationService) {
        this.vacancyAnalyzerService = vacancyAnalyzerService;
        this.biographyService = biographyService;
        this.biographyFileAnalyzerService = biographyFileAnalyzerService;
        this.biographyAiAnalyzerService = biographyAiAnalyzerService;
        this.anschreibenGeneratorService = anschreibenGeneratorService;
        this.changeDetectionService = changeDetectionService;
        this.fileOutputService = fileOutputService;
        this.pdfGenerationService = pdfGenerationService;
        this.gson = new Gson();
    }

    @PostMapping
    public ResponseEntity<?> generate(@Valid @RequestBody GenerateRequestDto request) {
        logger.info("Received generate request");
        
        // Validate biography is not empty (null is already handled by @NotNull)
        if (request.getBiography().isEmpty()) {
            throw new IllegalArgumentException("Biography must not be empty");
        }

        // Convert biography Map to text for storage
        String cvText = convertBiographyToText(request.getBiography());
        String vacancyText = request.getJobPosting();
        
        logger.info("Data received - Vacancy: {} chars, CV (biography): {} chars", 
            vacancyText != null ? vacancyText.length() : 0,
            cvText != null ? cvText.length() : 0);
        
        // Log preview of data for debugging
        if (vacancyText != null && !vacancyText.isEmpty()) {
            String vacancyPreview = vacancyText.length() > 200 ? vacancyText.substring(0, 200) + "..." : vacancyText;
            logger.debug("Vacancy preview: {}", vacancyPreview.replace("\n", "\\n"));
        }
        if (cvText != null && !cvText.isEmpty()) {
            String cvPreview = cvText.length() > 200 ? cvText.substring(0, 200) + "..." : cvText;
            logger.debug("CV preview: {}", cvPreview.replace("\n", "\\n"));
        }

        // Check for changes
        ChangeDetectionService.ChangeResult changeResult = changeDetectionService.checkAndSave(vacancyText, cvText);

        // If no changes detected, return early without AI processing
        if (!changeResult.hasChanges()) {
            logger.info("No changes detected. Skipping AI processing.");
            return ResponseEntity.ok("No changes detected. Existing analysis is still valid. AI processing skipped to save tokens.");
        }

        logger.info("Changes detected: {}", changeResult.getDescription());

        // Convert biography Map to JsonObject for processing
        String biographyJsonString = gson.toJson(request.getBiography());
        JsonObject biographyJson = gson.fromJson(biographyJsonString, JsonObject.class);
        Biography biography = biographyService.parseBiographyFromJson(biographyJson);

        // Analyze job posting (only if vacancy changed)
        JobRequirements jobRequirements = null;
        if (changeResult.isVacancyChanged()) {
            jobRequirements = vacancyAnalyzerService.analyzeVacancy(request.getJobPosting());
            fileOutputService.writeAnalysis(jobRequirements);
        } else {
            // Load existing analysis if available
            logger.info("Vacancy unchanged, skipping analysis");
        }

        // Generate cover letter (Anschreiben) - only if vacancy or CV changed
        if (changeResult.isVacancyChanged() || changeResult.isCvChanged()) {
            if (jobRequirements == null) {
                // Need to analyze even if vacancy didn't change, for anschreiben generation
                logger.info("Job requirements null, analyzing vacancy for anschreiben generation");
                jobRequirements = vacancyAnalyzerService.analyzeVacancy(request.getJobPosting());
            }
            logger.info("Generating anschreiben...");
            String coverLetter = anschreibenGeneratorService.generateAnschreiben(jobRequirements, biography, request.getJobPosting());
            logger.info("Anschreiben generated (length: {} chars), writing to file...", coverLetter.length());
            
            // Save to both output directory and data directory
            fileOutputService.writeAnschreiben(coverLetter);
            String dataAnschreibenPath = "data/anschreiben.txt";
            fileOutputService.writeAnschreiben(coverLetter, dataAnschreibenPath);
            
            // Save path to state
            changeDetectionService.saveAnschreibenPath(dataAnschreibenPath);
            
            logger.info("Anschreiben file write completed");
        } else {
            logger.info("No changes detected, skipping anschreiben generation");
        }

        // Write notes
        fileOutputService.writeNotes(changeResult.getDescription(), 
            changeResult.isVacancyChanged(), changeResult.isCvChanged());
        
        String outputPath = Paths.get("output/anschreiben.md").toAbsolutePath().toString();
        logger.info("Processing complete. Results written to output files.");
        logger.info("Anschreiben file location: {}", outputPath);
        return ResponseEntity.ok("Processing complete. Results written to output files. Anschreiben: " + outputPath);
    }

    @PostMapping("/cover-letter")
    public ResponseEntity<?> generateCoverLetter(@Valid @RequestBody GenerateRequestDto request) {
        logger.info("Received cover letter generation request");
        
        // Validate biography is not empty (null is already handled by @NotNull)
        if (request.getBiography().isEmpty()) {
            throw new IllegalArgumentException("Biography must not be empty");
        }

        // Convert biography Map to text for storage
        String cvText = convertBiographyToText(request.getBiography());
        String vacancyText = request.getJobPosting();

        // Check for changes
        ChangeDetectionService.ChangeResult changeResult = changeDetectionService.checkAndSave(vacancyText, cvText);

        // If no changes detected, try to load saved Anschreiben
        if (!changeResult.hasChanges()) {
            logger.info("No changes detected. Attempting to load saved Anschreiben...");
            String savedAnschreibenPath = changeDetectionService.getSavedAnschreibenPath();
            if (savedAnschreibenPath != null && !savedAnschreibenPath.isEmpty()) {
                String savedAnschreiben = fileOutputService.readAnschreiben(savedAnschreibenPath);
                if (savedAnschreiben != null && !savedAnschreiben.trim().isEmpty()) {
                    logger.info("Successfully loaded saved Anschreiben from: {}", savedAnschreibenPath);
                    // Also save to output directory for consistency
                    fileOutputService.writeAnschreiben(savedAnschreiben);
                    return ResponseEntity.ok("No changes detected. Using saved Anschreiben from: " + savedAnschreibenPath);
                }
            }
            logger.info("No saved Anschreiben found. Skipping AI processing.");
            return ResponseEntity.ok("No changes detected. Existing analysis is still valid. AI processing skipped to save tokens.");
        }

        logger.info("Changes detected: {}", changeResult.getDescription());

        // Convert biography Map to JsonObject for processing
        String biographyJsonString = gson.toJson(request.getBiography());
        JsonObject biographyJson = gson.fromJson(biographyJsonString, JsonObject.class);
        Biography biography = biographyService.parseBiographyFromJson(biographyJson);

        // Analyze job posting
        JobRequirements jobRequirements = vacancyAnalyzerService.analyzeVacancy(request.getJobPosting());
        if (changeResult.isVacancyChanged()) {
            fileOutputService.writeAnalysis(jobRequirements);
        }

        // Generate cover letter (Anschreiben)
        String coverLetter = anschreibenGeneratorService.generateAnschreiben(jobRequirements, biography, request.getJobPosting());
        
        // Save to both output directory and data directory
        fileOutputService.writeAnschreiben(coverLetter);
        String dataAnschreibenPath = "data/anschreiben.txt";
        fileOutputService.writeAnschreiben(coverLetter, dataAnschreibenPath);
        
        // Save path to state
        changeDetectionService.saveAnschreibenPath(dataAnschreibenPath);

        // Write notes
        fileOutputService.writeNotes(changeResult.getDescription(), 
            changeResult.isVacancyChanged(), changeResult.isCvChanged());
        
        logger.info("Cover letter generated and written to output files");
        return ResponseEntity.ok("Cover letter generated and written to output files");
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

        // Read file content for change detection
        String biographyText;
        try {
            biographyText = new String(biographyFile.getBytes(), StandardCharsets.UTF_8);
            logger.info("Read biography file - length: {} chars", biographyText.length());
        } catch (Exception e) {
            logger.error("Error reading biography file", e);
            throw new IllegalArgumentException("Failed to read biography file", e);
        }

        logger.info("Checking changes - Job posting length: {} chars, Biography length: {} chars", 
            jobPosting != null ? jobPosting.length() : 0,
            biographyText != null ? biographyText.length() : 0);

        // Check for changes
        ChangeDetectionService.ChangeResult changeResult = changeDetectionService.checkAndSave(jobPosting, biographyText);
        
        logger.info("Change detection result - hasChanges: {}, vacancyChanged: {}, cvChanged: {}", 
            changeResult.hasChanges(), changeResult.isVacancyChanged(), changeResult.isCvChanged());

        // If no changes detected, try to load saved Anschreiben
        if (!changeResult.hasChanges()) {
            logger.info("No changes detected. Attempting to load saved Anschreiben...");
            String savedAnschreibenPath = changeDetectionService.getSavedAnschreibenPath();
            if (savedAnschreibenPath != null && !savedAnschreibenPath.isEmpty()) {
                String savedAnschreiben = fileOutputService.readAnschreiben(savedAnschreibenPath);
                if (savedAnschreiben != null && !savedAnschreiben.trim().isEmpty()) {
                    logger.info("Successfully loaded saved Anschreiben from: {}", savedAnschreibenPath);
                    // Also save to output directory for consistency
                    fileOutputService.writeAnschreiben(savedAnschreiben);
                    GenerateResponseDto response = new GenerateResponseDto(savedAnschreiben);
                    return ResponseEntity.ok(response);
                }
            }
            logger.info("No saved Anschreiben found. Will generate new one.");
        }

        // Read file content and parse using AI
        Biography biography;
        try {
            // Use AI parser for free-form text
            biography = biographyAiAnalyzerService.parseBiography(biographyText);
        } catch (Exception e) {
            logger.error("Error parsing biography with AI, falling back to file parser", e);
            // Fallback to structured file parser if AI parsing fails
            biography = biographyFileAnalyzerService.parseBiographyFromFile(biographyFile);
        }

        // Analyze job posting
        JobRequirements jobRequirements = vacancyAnalyzerService.analyzeVacancy(jobPosting);

        // Generate cover letter (Anschreiben) - pass full biography for experience/education reference
        String coverLetter = anschreibenGeneratorService.generateAnschreiben(jobRequirements, biography, jobPosting);
        
        // Save to both output directory and data directory
        fileOutputService.writeAnschreiben(coverLetter);
        String dataAnschreibenPath = "data/anschreiben.txt";
        fileOutputService.writeAnschreiben(coverLetter, dataAnschreibenPath);
        
        // Save path to state
        changeDetectionService.saveAnschreibenPath(dataAnschreibenPath);

        // Build response
        GenerateResponseDto response = new GenerateResponseDto(coverLetter);
        
        logger.info("Successfully generated cover letter from file");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pdf")
    public ResponseEntity<byte[]> generatePdf(@RequestBody PdfRequestDto request) {
        String coverLetterText = request.getCoverLetter();
        logger.info("Received PDF generation request (text length: {} chars)", 
            coverLetterText != null ? coverLetterText.length() : 0);
        
        if (coverLetterText == null || coverLetterText.trim().isEmpty()) {
            throw new IllegalArgumentException("Cover letter text must not be empty");
        }
        
        try {
            byte[] pdfBytes = pdfGenerationService.generatePdf(coverLetterText);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "Anschreiben.pdf");
            headers.setContentLength(pdfBytes.length);
            
            logger.info("PDF generated successfully ({} bytes)", pdfBytes.length);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
        } catch (Exception e) {
            logger.error("Error generating PDF", e);
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }

    private String convertBiographyToText(java.util.Map<String, Object> biography) {
        // Convert biography Map to JSON string for storage
        return gson.toJson(biography);
    }
}

