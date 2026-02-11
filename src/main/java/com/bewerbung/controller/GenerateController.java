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
        
        logger.info("Data received - Vacancy: {} chars, CV (biography): {} chars, Wishes: {} chars", 
            vacancyText != null ? vacancyText.length() : 0,
            cvText != null ? cvText.length() : 0,
            request.getWishes() != null ? request.getWishes().length() : 0);
        
        // Check if data matches default samples - if so, use sample cover letter without AI
        // BUT: if wishes are provided, we need to regenerate even for default data
        if (fileOutputService.isDefaultData(vacancyText, cvText) && (request.getWishes() == null || request.getWishes().trim().isEmpty())) {
            logger.info("Data matches default samples - using sample cover letter without AI processing");
            String sampleCoverLetter = fileOutputService.loadSampleCoverLetter();
            if (sampleCoverLetter != null && !sampleCoverLetter.trim().isEmpty()) {
                // Save to output files
                fileOutputService.writeAnschreiben(sampleCoverLetter);
                String dataAnschreibenPath = "data/anschreiben.txt";
                fileOutputService.writeAnschreiben(sampleCoverLetter, dataAnschreibenPath);
                changeDetectionService.saveAnschreibenPath(dataAnschreibenPath);
                
                logger.info("Sample cover letter loaded and saved to output files ({} chars)", sampleCoverLetter.length());
                String outputPath = java.nio.file.Paths.get("output/anschreiben.md").toAbsolutePath().toString();
                return ResponseEntity.ok("Using sample cover letter (default data detected, AI skipped). File: " + outputPath);
            } else {
                logger.warn("Sample cover letter not found, falling back to AI generation");
            }
        }
        
        // Log preview of data for debugging
        if (vacancyText != null && !vacancyText.isEmpty()) {
            String vacancyPreview = vacancyText.length() > 200 ? vacancyText.substring(0, 200) + "..." : vacancyText;
            logger.debug("Vacancy preview: {}", vacancyPreview.replace("\n", "\\n"));
        }
        if (cvText != null && !cvText.isEmpty()) {
            String cvPreview = cvText.length() > 200 ? cvText.substring(0, 200) + "..." : cvText;
            logger.debug("CV preview: {}", cvPreview.replace("\n", "\\n"));
        }

        // Check for changes (including wishes)
        ChangeDetectionService.ChangeResult changeResult = changeDetectionService.checkAndSave(vacancyText, cvText, request.getWishes());

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

        // Generate cover letter (Anschreiben) - only if vacancy, CV, or wishes changed
        if (changeResult.isVacancyChanged() || changeResult.isCvChanged() || changeResult.isWishesChanged()) {
            String coverLetter;
            
            // Special case: if only wishes changed (not vacancy or CV), check if we can apply corrections
            if (changeResult.isWishesChanged() && !changeResult.isVacancyChanged() && !changeResult.isCvChanged()) {
                // Check if wishes contain FACT_EXCLUSION (deletions) - if yes, need full regeneration
                boolean hasFactExclusion = anschreibenGeneratorService.containsFactExclusion(request.getWishes());
                
                if (hasFactExclusion) {
                    logger.info("Wishes contain FACT_EXCLUSION (deletions) - must regenerate letter from scratch using all fields");
                    // Need full generation when deletions are present
                    if (jobRequirements == null) {
                        logger.info("Job requirements null, analyzing vacancy for anschreiben generation");
                        jobRequirements = vacancyAnalyzerService.analyzeVacancy(request.getJobPosting());
                    }
                    coverLetter = anschreibenGeneratorService.generateAnschreiben(
                            jobRequirements, biography, request.getJobPosting(), request.getWishes());
                } else {
                    logger.info("Only wishes changed (no deletions) - attempting to apply corrections to existing anschreiben...");
                    
                    // Try to load existing anschreiben
                    String savedAnschreibenPath = changeDetectionService.getSavedAnschreibenPath();
                    String existingAnschreiben = null;
                    
                    if (savedAnschreibenPath != null && !savedAnschreibenPath.isEmpty()) {
                        existingAnschreiben = fileOutputService.readAnschreiben(savedAnschreibenPath);
                    }
                    
                    // Also try to read from default output location
                    if (existingAnschreiben == null || existingAnschreiben.trim().isEmpty()) {
                        existingAnschreiben = fileOutputService.readAnschreiben("output/anschreiben.md");
                    }
                    
                    if (existingAnschreiben != null && !existingAnschreiben.trim().isEmpty()) {
                        logger.info("Found existing anschreiben (length: {} chars), applying corrections...", existingAnschreiben.length());
                        // Default to German if language not specified
                        String language = request.getLanguage();
                        if (language == null || language.trim().isEmpty()) {
                            language = "de";
                        }
                        // Ensure we have job requirements for reference
                        if (jobRequirements == null) {
                            logger.info("Job requirements null, analyzing vacancy for reference data");
                            jobRequirements = vacancyAnalyzerService.analyzeVacancy(request.getJobPosting());
                        }
                        coverLetter = anschreibenGeneratorService.applyCorrectionsToAnschreiben(
                                existingAnschreiben, request.getWishes(), language, 
                                jobRequirements, biography, request.getJobPosting());
                        logger.info("Corrections applied successfully (length: {} chars)", coverLetter.length());
                    } else {
                        logger.warn("No existing anschreiben found, falling back to full generation");
                        // Fallback to full generation if no existing letter found
                        if (jobRequirements == null) {
                            logger.info("Job requirements null, analyzing vacancy for anschreiben generation");
                            jobRequirements = vacancyAnalyzerService.analyzeVacancy(request.getJobPosting());
                        }
                        coverLetter = anschreibenGeneratorService.generateAnschreiben(
                                jobRequirements, biography, request.getJobPosting(), request.getWishes());
                    }
                }
            } else {
                // Full generation needed (vacancy or CV changed, or wishes changed but no existing letter)
                if (jobRequirements == null) {
                    // Need to analyze even if vacancy didn't change, for anschreiben generation
                    logger.info("Job requirements null, analyzing vacancy for anschreiben generation");
                    jobRequirements = vacancyAnalyzerService.analyzeVacancy(request.getJobPosting());
                }
                logger.info("Generating anschreiben from scratch...");
                coverLetter = anschreibenGeneratorService.generateAnschreiben(
                        jobRequirements, biography, request.getJobPosting(), request.getWishes());
            }
            
            logger.info("Anschreiben ready (length: {} chars), writing to file...", coverLetter.length());
            
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

        // Default to German if language not specified
        String language = request.getLanguage();
        if (language == null || language.trim().isEmpty()) {
            language = "de";
        }

        // Check for changes (including wishes and language)
        ChangeDetectionService.ChangeResult changeResult = changeDetectionService.checkAndSave(vacancyText, cvText, request.getWishes(), language);

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

        // Generate cover letter (Anschreiben) - use the same language from request
        String coverLetter;
        
        // Special case: if only wishes changed (not vacancy, CV, or language), check if we can apply corrections
        // IMPORTANT: If language changed, we must regenerate completely even if only wishes changed
        if (changeResult.isWishesChanged() && !changeResult.isVacancyChanged() && !changeResult.isCvChanged() && !changeResult.isLanguageChanged()) {
            // Check if wishes contain FACT_EXCLUSION (deletions) - if yes, need full regeneration
            boolean hasFactExclusion = anschreibenGeneratorService.containsFactExclusion(request.getWishes());
            
            if (hasFactExclusion) {
                logger.info("Wishes contain FACT_EXCLUSION (deletions) - must regenerate letter from scratch using all fields");
                // Need full generation when deletions are present
                coverLetter = anschreibenGeneratorService.generateAnschreiben(
                        jobRequirements, biography, request.getJobPosting(), request.getWishes(), language);
            } else {
                logger.info("Only wishes changed (no deletions, language unchanged) - attempting to apply corrections to existing anschreiben...");
                
                // Try to load existing anschreiben
                String savedAnschreibenPath = changeDetectionService.getSavedAnschreibenPath();
                String existingAnschreiben = null;
                
                if (savedAnschreibenPath != null && !savedAnschreibenPath.isEmpty()) {
                    existingAnschreiben = fileOutputService.readAnschreiben(savedAnschreibenPath);
                }
                
                // Also try to read from default output location
                if (existingAnschreiben == null || existingAnschreiben.trim().isEmpty()) {
                    existingAnschreiben = fileOutputService.readAnschreiben("output/anschreiben.md");
                }
                
                if (existingAnschreiben != null && !existingAnschreiben.trim().isEmpty()) {
                    logger.info("Found existing anschreiben (length: {} chars), applying corrections...", existingAnschreiben.length());
                    coverLetter = anschreibenGeneratorService.applyCorrectionsToAnschreiben(
                            existingAnschreiben, request.getWishes(), language, 
                            jobRequirements, biography, request.getJobPosting());
                    logger.info("Corrections applied successfully (length: {} chars)", coverLetter.length());
                } else {
                    logger.warn("No existing anschreiben found, falling back to full generation");
                    // Fallback to full generation if no existing letter found
                    coverLetter = anschreibenGeneratorService.generateAnschreiben(
                            jobRequirements, biography, request.getJobPosting(), request.getWishes(), language);
                }
            }
        } else {
            // Full generation needed (vacancy, CV, or language changed, or wishes changed but no existing letter)
            if (changeResult.isLanguageChanged()) {
                logger.info("Language changed - must regenerate cover letter completely");
            }
            logger.info("Generating anschreiben from scratch...");
            coverLetter = anschreibenGeneratorService.generateAnschreiben(
                    jobRequirements, biography, request.getJobPosting(), request.getWishes(), language);
        }
        
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
            @RequestParam("jobPosting") String jobPosting,
            @RequestParam(value = "wishes", required = false) String wishes,
            @RequestParam(value = "language", required = false) String language) {
        logger.info("Received generate request from file");
        logger.info("Requested language parameter: {}", language != null ? language : "null");
        
        // Log user wishes if provided
        if (wishes != null && !wishes.trim().isEmpty()) {
            logger.info("User wishes/corrections received (length: {} chars): {}", wishes.length(), wishes);
        } else {
            logger.info("No user wishes/corrections provided");
        }
        
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

        // Default to German if language not specified
        if (language == null || language.trim().isEmpty()) {
            language = "de";
        }
        logger.info("Requested language: {}", language);

        // Check if data matches default samples - if so, use sample cover letter without AI
        // BUT: if wishes are provided, we need to regenerate even for default data
        // Use language-specific sample file
        if (fileOutputService.isDefaultData(jobPosting, biographyText, language) 
                && (wishes == null || wishes.trim().isEmpty())) {
            logger.info("Data matches default samples for language '{}' and no wishes provided - using sample cover letter without AI processing", language);
            String sampleCoverLetter = fileOutputService.loadSampleCoverLetter(language);
            if (sampleCoverLetter != null && !sampleCoverLetter.trim().isEmpty()) {
                // Save to output files
                fileOutputService.writeAnschreiben(sampleCoverLetter);
                String dataAnschreibenPath = "data/anschreiben.txt";
                fileOutputService.writeAnschreiben(sampleCoverLetter, dataAnschreibenPath);
                changeDetectionService.saveAnschreibenPath(dataAnschreibenPath);
                
                logger.info("Sample cover letter loaded and saved to output files ({} chars, language: {})", sampleCoverLetter.length(), language);
                GenerateResponseDto response = new GenerateResponseDto(sampleCoverLetter);
                return ResponseEntity.ok(response);
            } else {
                logger.warn("Sample cover letter not found for language '{}', falling back to AI generation", language);
            }
        } else if (fileOutputService.isDefaultData(jobPosting, biographyText, language) 
                && wishes != null && !wishes.trim().isEmpty()) {
            logger.info("Data matches default samples but wishes are provided - will use AI to generate cover letter with wishes");
        }
        
        // Check for changes (including wishes and language)
        ChangeDetectionService.ChangeResult changeResult = changeDetectionService.checkAndSave(jobPosting, biographyText, wishes, language);
        
        logger.info("Change detection result - hasChanges: {}, vacancyChanged: {}, cvChanged: {}, wishesChanged: {}, languageChanged: {}", 
            changeResult.hasChanges(), changeResult.isVacancyChanged(), changeResult.isCvChanged(), 
            changeResult.isWishesChanged(), changeResult.isLanguageChanged());

        // If no changes detected AND language hasn't changed, try to load saved Anschreiben
        // IMPORTANT: If language changed, we must regenerate even if other data hasn't changed
        if (!changeResult.hasChanges() && !changeResult.isLanguageChanged()) {
            logger.info("No changes detected and language hasn't changed. Attempting to load saved Anschreiben...");
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
        } else if (changeResult.isLanguageChanged()) {
            logger.info("Language changed to: {}. Must regenerate cover letter even if other data hasn't changed.", language);
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

        // Default to German if language not specified
        String languageForGeneration = language;
        if (languageForGeneration == null || languageForGeneration.trim().isEmpty()) {
            languageForGeneration = "de";
        }
        logger.info("Generating cover letter in language: {}", languageForGeneration);

        // Generate cover letter (Anschreiben) - pass full biography for experience/education reference
        String coverLetter;
        
        // Special case: if only wishes changed (not vacancy, CV, or language), check if we can apply corrections
        // IMPORTANT: If language changed, we must regenerate completely even if only wishes changed
        if (changeResult.isWishesChanged() && !changeResult.isVacancyChanged() && !changeResult.isCvChanged() && !changeResult.isLanguageChanged()) {
            // Check if wishes contain FACT_EXCLUSION (deletions) - if yes, need full regeneration
            boolean hasFactExclusion = anschreibenGeneratorService.containsFactExclusion(wishes);
            
            if (hasFactExclusion) {
                logger.info("Wishes contain FACT_EXCLUSION (deletions) - must regenerate letter from scratch using all fields");
                // Need full generation when deletions are present
                coverLetter = anschreibenGeneratorService.generateAnschreiben(
                        jobRequirements, biography, jobPosting, wishes, languageForGeneration);
            } else {
                logger.info("Only wishes changed (no deletions, language unchanged) - attempting to apply corrections to existing anschreiben...");
                
                // Try to load existing anschreiben
                String savedAnschreibenPath = changeDetectionService.getSavedAnschreibenPath();
                String existingAnschreiben = null;
                
                if (savedAnschreibenPath != null && !savedAnschreibenPath.isEmpty()) {
                    existingAnschreiben = fileOutputService.readAnschreiben(savedAnschreibenPath);
                }
                
                // Also try to read from default output location
                if (existingAnschreiben == null || existingAnschreiben.trim().isEmpty()) {
                    existingAnschreiben = fileOutputService.readAnschreiben("output/anschreiben.md");
                }
                
                if (existingAnschreiben != null && !existingAnschreiben.trim().isEmpty()) {
                    logger.info("Found existing anschreiben (length: {} chars), applying corrections...", existingAnschreiben.length());
                    coverLetter = anschreibenGeneratorService.applyCorrectionsToAnschreiben(
                            existingAnschreiben, wishes, languageForGeneration, 
                            jobRequirements, biography, jobPosting);
                    logger.info("Corrections applied successfully (length: {} chars)", coverLetter.length());
                } else {
                    logger.warn("No existing anschreiben found, falling back to full generation");
                    // Fallback to full generation if no existing letter found
                    coverLetter = anschreibenGeneratorService.generateAnschreiben(
                            jobRequirements, biography, jobPosting, wishes, languageForGeneration);
                }
            }
        } else {
            // Full generation needed (vacancy, CV, or language changed, or wishes changed but no existing letter)
            if (changeResult.isLanguageChanged()) {
                logger.info("Language changed - must regenerate cover letter completely");
            }
            logger.info("Generating anschreiben from scratch...");
            coverLetter = anschreibenGeneratorService.generateAnschreiben(
                    jobRequirements, biography, jobPosting, wishes, languageForGeneration);
        }
        
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

