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
import com.bewerbung.service.LebenslaufTemplateService;
import com.bewerbung.service.PdfGenerationService;
import com.bewerbung.service.TempPhotoStorageService;
import com.bewerbung.service.VacancyAnalyzerService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.io.IOException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.InputStream;
import java.util.Base64;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.w3c.dom.Document;

@RestController
@RequestMapping("/api/generate")
public class GenerateController {

    private static final Logger logger = LoggerFactory.getLogger(GenerateController.class);
    private static final Pattern TITLE_NAME_PATTERN = Pattern.compile(
            "<title>\\s*Lebenslauf\\s*[\\u2013\\-]\\s*([^<]+)</title>",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NAME_BLOCK_PATTERN = Pattern.compile(
            "<h1[^>]*class\\s*=\\s*\"name-block\"[^>]*>(.*?)</h1>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern HTML_TAGS_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern NON_ASCII_FILENAME_PATTERN = Pattern.compile("[^A-Za-z0-9_-]");

    private final VacancyAnalyzerService vacancyAnalyzerService;
    private final BiographyService biographyService;
    private final BiographyFileAnalyzerService biographyFileAnalyzerService;
    private final BiographyAiAnalyzerService biographyAiAnalyzerService;
    private final AnschreibenGeneratorService anschreibenGeneratorService;
    private final ChangeDetectionService changeDetectionService;
    private final FileOutputService fileOutputService;
    private final LebenslaufTemplateService lebenslaufTemplateService;
    private final PdfGenerationService pdfGenerationService;
    private final TempPhotoStorageService tempPhotoStorageService;
    private final Gson gson;

    @Autowired
    public GenerateController(VacancyAnalyzerService vacancyAnalyzerService,
                             BiographyService biographyService,
                             BiographyFileAnalyzerService biographyFileAnalyzerService,
                             BiographyAiAnalyzerService biographyAiAnalyzerService,
                             AnschreibenGeneratorService anschreibenGeneratorService,
                             ChangeDetectionService changeDetectionService,
                             FileOutputService fileOutputService,
                             LebenslaufTemplateService lebenslaufTemplateService,
                             PdfGenerationService pdfGenerationService,
                             TempPhotoStorageService tempPhotoStorageService) {
        this.vacancyAnalyzerService = vacancyAnalyzerService;
        this.biographyService = biographyService;
        this.biographyFileAnalyzerService = biographyFileAnalyzerService;
        this.biographyAiAnalyzerService = biographyAiAnalyzerService;
        this.anschreibenGeneratorService = anschreibenGeneratorService;
        this.changeDetectionService = changeDetectionService;
        this.fileOutputService = fileOutputService;
        this.lebenslaufTemplateService = lebenslaufTemplateService;
        this.pdfGenerationService = pdfGenerationService;
        this.tempPhotoStorageService = tempPhotoStorageService;
        this.gson = new Gson();
    }

    @PostMapping("/upload-photo")
    public ResponseEntity<Map<String, String>> uploadResumePhoto(@RequestParam("photo") MultipartFile photo) {
        logger.info("Received CV photo upload request");
        String photoId = tempPhotoStorageService.saveTemporaryPhoto(photo);
        return ResponseEntity.ok(Map.of(
                "message", "Photo uploaded successfully",
                "photoId", photoId
        ));
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

                // Also regenerate lebenslauf to avoid serving stale file from previous session.
                try {
                    String biographyJsonString = gson.toJson(request.getBiography());
                    JsonObject biographyJson = gson.fromJson(biographyJsonString, JsonObject.class);
                    String lebenslaufHtml = lebenslaufTemplateService.generateLebenslauf(biographyJson);
                    fileOutputService.writeLebenslauf(lebenslaufHtml);
                    logger.info("Lebenslauf regenerated for default data flow");
                } catch (Exception e) {
                    logger.error("Failed to generate lebenslauf in default-data flow", e);
                    throw new RuntimeException("Failed to generate lebenslauf for default data", e);
                }
                
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

        // Convert biography Map to JsonObject for processing (needed for both anschreiben and lebenslauf)
        String biographyJsonString = gson.toJson(request.getBiography());
        JsonObject biographyJson = gson.fromJson(biographyJsonString, JsonObject.class);
        Biography biography = biographyService.parseBiographyFromJson(biographyJson);

        // Check for changes (including wishes)
        ChangeDetectionService.ChangeResult changeResult = changeDetectionService.checkAndSave(vacancyText, cvText, request.getWishes());

        // If no changes detected, return early without AI processing (but still generate lebenslauf)
        if (!changeResult.hasChanges()) {
            logger.info("No changes detected. Skipping AI processing for anschreiben.");
            
            // Still generate lebenslauf even if no changes detected
            try {
                logger.info("Generating lebenslauf from biography data (no changes detected, but generating CV)");
                String lebenslaufHtml = lebenslaufTemplateService.generateLebenslauf(biographyJson);
                fileOutputService.writeLebenslauf(lebenslaufHtml);
                logger.info("Lebenslauf generated and saved successfully");
            } catch (Exception e) {
                logger.error("Error generating lebenslauf, continuing without it", e);
            }
            
            return ResponseEntity.ok("No changes detected. Existing analysis is still valid. AI processing skipped to save tokens. Lebenslauf generated.");
        }

        logger.info("Changes detected: {}", changeResult.getDescription());

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

        // Generate and save lebenslauf (CV)
        try {
            // Use already parsed biographyJson from above (line 137)
            logger.info("Generating lebenslauf from biography data");
            String lebenslaufHtml = lebenslaufTemplateService.generateLebenslauf(biographyJson);
            fileOutputService.writeLebenslauf(lebenslaufHtml);
            logger.info("Lebenslauf generated and saved successfully");
        } catch (Exception e) {
            logger.error("Error generating lebenslauf, continuing without it", e);
            // Не прерываем выполнение, если lebenslauf не удалось сгенерировать
        }

        // Write notes
        fileOutputService.writeNotes(changeResult.getDescription(), 
            changeResult.isVacancyChanged(), changeResult.isCvChanged());
        
        String outputPath = Paths.get("output/anschreiben.md").toAbsolutePath().toString();
        String lebenslaufPath = Paths.get("output/lebenslauf-filled.html").toAbsolutePath().toString();
        logger.info("Processing complete. Results written to output files.");
        logger.info("Anschreiben file location: {}", outputPath);
        logger.info("Lebenslauf file location: {}", lebenslaufPath);
        return ResponseEntity.ok("Processing complete. Results written to output files. Anschreiben: " + outputPath + ", Lebenslauf: " + lebenslaufPath);
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

        // Convert biography Map to JsonObject for processing (needed for both anschreiben and lebenslauf)
        String biographyJsonString = gson.toJson(request.getBiography());
        JsonObject biographyJson = gson.fromJson(biographyJsonString, JsonObject.class);
        Biography biography = biographyService.parseBiographyFromJson(biographyJson);

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
                    
                    // Still generate lebenslauf even if no changes detected
                    try {
                        logger.info("Generating lebenslauf from biography data (no changes detected, but generating CV)");
                        String lebenslaufHtml = lebenslaufTemplateService.generateLebenslauf(biographyJson);
                        fileOutputService.writeLebenslauf(lebenslaufHtml);
                        logger.info("Lebenslauf generated and saved successfully");
                    } catch (Exception e) {
                        logger.error("Error generating lebenslauf, continuing without it", e);
                    }
                    
                    return ResponseEntity.ok("No changes detected. Using saved Anschreiben from: " + savedAnschreibenPath + ". Lebenslauf generated.");
                }
            }
            logger.info("No saved Anschreiben found. Skipping AI processing.");
            
            // Still generate lebenslauf even if no changes detected
            try {
                logger.info("Generating lebenslauf from biography data (no changes detected, but generating CV)");
                String lebenslaufHtml = lebenslaufTemplateService.generateLebenslauf(biographyJson);
                fileOutputService.writeLebenslauf(lebenslaufHtml);
                logger.info("Lebenslauf generated and saved successfully");
            } catch (Exception e) {
                logger.error("Error generating lebenslauf, continuing without it", e);
            }
            
            return ResponseEntity.ok("No changes detected. Existing analysis is still valid. AI processing skipped to save tokens. Lebenslauf generated.");
        }

        logger.info("Changes detected: {}", changeResult.getDescription());

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

        // Generate and save lebenslauf (CV)
        try {
            // Convert biography Map to JsonObject for lebenslauf generation
            String biographyJsonStr = gson.toJson(request.getBiography());
            JsonObject biographyJsonForLebenslauf = gson.fromJson(biographyJsonStr, JsonObject.class);
            
            logger.info("Generating lebenslauf from biography data");
            String lebenslaufHtml = lebenslaufTemplateService.generateLebenslauf(biographyJsonForLebenslauf);
            fileOutputService.writeLebenslauf(lebenslaufHtml);
            logger.info("Lebenslauf generated and saved successfully");
        } catch (Exception e) {
            logger.error("Error generating lebenslauf, continuing without it", e);
            // Не прерываем выполнение, если lebenslauf не удалось сгенерировать
        }

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

        // Generate and save lebenslauf (CV)
        try {
            logger.info("Generating lebenslauf from Biography object");
            String lebenslaufHtml = lebenslaufTemplateService.generateLebenslauf(biography);
            fileOutputService.writeLebenslauf(lebenslaufHtml);
            logger.info("Lebenslauf generated and saved successfully");
        } catch (Exception e) {
            logger.error("Error generating lebenslauf, continuing without it", e);
            // Не прерываем выполнение, если lebenslauf не удалось сгенерировать
        }

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
        } catch (IOException e) {
            logger.error("IO error generating PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error generating PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Serves the same UTF-8 font used for Anschreiben PDF (from resources/fonts/).
     * Used by the Lebenslauf HTML so that PDF export uses the same font when rendered by Chrome.
     */
    @GetMapping(value = "/fonts/LiberationSans-Regular.ttf", produces = "font/ttf")
    public ResponseEntity<byte[]> getLebenslaufFont() {
        try (InputStream fontStream = getClass().getResourceAsStream("/fonts/LiberationSans-Regular.ttf")) {
            if (fontStream == null) {
                logger.warn("Font resource /fonts/LiberationSans-Regular.ttf not found");
                return ResponseEntity.notFound().build();
            }
            byte[] fontBytes = fontStream.readAllBytes();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("font/ttf"));
            headers.setCacheControl("public, max-age=86400");
            return ResponseEntity.ok().headers(headers).body(fontBytes);
        } catch (IOException e) {
            logger.error("Failed to read font resource", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/lebenslauf/html")
    public ResponseEntity<String> getLebenslaufHtml() {
        Path htmlPath = Paths.get("output", "lebenslauf-filled.html").toAbsolutePath();
        if (!Files.exists(htmlPath)) {
            throw new RuntimeException("Lebenslauf HTML file not found: " + htmlPath);
        }

        try {
            String html = Files.readString(htmlPath, StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                    .body(html);
        } catch (IOException e) {
            logger.error("Failed to read lebenslauf HTML from {}", htmlPath, e);
            throw new RuntimeException("Failed to read lebenslauf HTML", e);
        }
    }

    @GetMapping("/lebenslauf/default-html")
    public ResponseEntity<String> getDefaultLebenslaufHtml() {
        try {
            ClassPathResource resource = new ClassPathResource("lebenslauf-filled.html");
            if (!resource.exists()) {
                throw new RuntimeException("Default lebenslauf HTML template not found in resources");
            }

            String html = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            // Ensure absolute static path for image and other static assets during PDF rendering.
            String normalizedHtml = html.replace("src=\"static/", "src=\"/static/");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                    .body(normalizedHtml);
        } catch (IOException e) {
            logger.error("Failed to read default lebenslauf HTML template", e);
            throw new RuntimeException("Failed to read default lebenslauf HTML template", e);
        }
    }

    @GetMapping("/pdf/lebenslauf")
    public ResponseEntity<byte[]> generateLebenslaufPdf(
            HttpServletRequest request,
            @RequestParam(name = "defaultData", defaultValue = "false") boolean defaultData,
            @RequestParam(name = "force", defaultValue = "false") boolean forceRegenerate
    ) {
        Path htmlPath = Paths.get("output", "lebenslauf-filled.html").toAbsolutePath();

        final String filename;
        final Path outputPdfPath;
        final String sourceUrl;

        if (defaultData) {
            filename = "Musterman_Lebenslauf.PDF";
            outputPdfPath = Paths.get("output", filename).toAbsolutePath();
            sourceUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
                    + "/api/generate/lebenslauf/default-html";
        } else {
            if (!Files.exists(htmlPath)) {
                throw new RuntimeException("Lebenslauf HTML file not found. Please generate Lebenslauf first.");
            }
            String surname = extractSurnameFromLebenslaufHtml(htmlPath);
            filename = surname + "_LebensLauf.PDF";
            outputPdfPath = Paths.get("output", filename).toAbsolutePath();
            sourceUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
                    + "/api/generate/lebenslauf/html";
        }

        // Check if PDF needs to be regenerated
        // Regenerate if: force flag is set, PDF doesn't exist, or HTML file is newer than PDF
        boolean needsRegeneration = false;
        if (forceRegenerate) {
            needsRegeneration = true;
            logger.info("Force regeneration requested, will regenerate PDF");
        } else {
            try {
                if (!Files.exists(outputPdfPath) || Files.size(outputPdfPath) == 0) {
                    needsRegeneration = true;
                    logger.debug("PDF file does not exist or is empty, will regenerate");
                } else if (!defaultData && Files.exists(htmlPath)) {
                    // Check if HTML file is newer than PDF
                    long htmlLastModified = Files.getLastModifiedTime(htmlPath).toMillis();
                    long pdfLastModified = Files.getLastModifiedTime(outputPdfPath).toMillis();
                    if (htmlLastModified > pdfLastModified) {
                        needsRegeneration = true;
                        logger.info("HTML file is newer than PDF (HTML: {}, PDF: {}), will regenerate PDF", 
                                new java.util.Date(htmlLastModified), new java.util.Date(pdfLastModified));
                    } else {
                        logger.debug("PDF file is up to date (HTML: {}, PDF: {})", 
                                new java.util.Date(htmlLastModified), new java.util.Date(pdfLastModified));
                    }
                }
            } catch (IOException e) {
                logger.warn("Error checking file timestamps, will regenerate PDF: {}", e.getMessage());
                needsRegeneration = true;
            }
        }

        if (needsRegeneration) {
            boolean generated = generateLebenslaufPdfWithChrome(sourceUrl, outputPdfPath);
            if (!generated && !defaultData && Files.exists(htmlPath)) {
                logger.info("Chrome/Chromium not available, using Java fallback (OpenHTML to PDF)");
                generated = generateLebenslaufPdfWithOpenHtml(htmlPath, outputPdfPath);
            }
            if (!generated) {
                throw new RuntimeException("Failed to generate Lebenslauf PDF. Install google-chrome/chromium or ensure font is in resources/fonts/.");
            }
        } else {
            logger.info("PDF file is up to date, using existing file: {}", outputPdfPath);
        }

        try {
            byte[] pdfBytes = Files.readAllBytes(outputPdfPath);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(pdfBytes.length);

            logger.info("Lebenslauf PDF generated successfully: {} ({} bytes)", outputPdfPath, pdfBytes.length);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
        } catch (IOException e) {
            logger.error("Failed to read generated lebenslauf PDF from {}", outputPdfPath, e);
            throw new RuntimeException("Failed to read generated lebenslauf PDF", e);
        }
    }

    private boolean generateLebenslaufPdfWithChrome(String sourceUrl, Path outputPdfPath) {
        try {
            Path parent = outputPdfPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            logger.error("Failed to create output directory for PDF {}", outputPdfPath, e);
            return false;
        }

        List<List<String>> commands = Arrays.asList(
                Arrays.asList(
                        "google-chrome", "--headless", "--disable-gpu", "--no-sandbox",
                        "--print-to-pdf-no-header",
                        "--print-to-pdf=" + outputPdfPath.toString(),
                        sourceUrl
                ),
                Arrays.asList(
                        "chromium-browser", "--headless", "--disable-gpu", "--no-sandbox",
                        "--print-to-pdf-no-header",
                        "--print-to-pdf=" + outputPdfPath.toString(),
                        sourceUrl
                ),
                Arrays.asList(
                        "chromium", "--headless", "--disable-gpu", "--no-sandbox",
                        "--print-to-pdf-no-header",
                        "--print-to-pdf=" + outputPdfPath.toString(),
                        sourceUrl
                )
        );

        for (List<String> command : commands) {
            try {
                Process process = new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .start();
                boolean finished = process.waitFor(60, TimeUnit.SECONDS);
                String processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

                if (!finished) {
                    process.destroyForcibly();
                    logger.warn("Timeout while generating Lebenslauf PDF with command: {}", command.get(0));
                    continue;
                }

                if (process.exitValue() == 0 && Files.exists(outputPdfPath) && Files.size(outputPdfPath) > 0) {
                    logger.info("Lebenslauf PDF generated via {}: {}", command.get(0), outputPdfPath);
                    return true;
                }

                logger.warn(
                        "PDF generation command '{}' failed with exit code {}. Output: {}",
                        command.get(0), process.exitValue(), processOutput
                );
            } catch (Exception e) {
                logger.warn("Failed to execute PDF generation command '{}': {}", command.get(0), e.getMessage());
            }
        }

        return false;
    }

    /**
     * Generates Lebenslauf PDF using OpenHTML to PDF (pure Java, no Chrome).
     * Uses the same font from resources/fonts/ as the Anschreiben PDF.
     */
    private boolean generateLebenslaufPdfWithOpenHtml(Path htmlPath, Path outputPdfPath) {
        try {
            Path parent = outputPdfPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            logger.error("Failed to create output directory for PDF {}", outputPdfPath, e);
            return false;
        }

        String html;
        try {
            if (Files.exists(htmlPath)) {
                html = Files.readString(htmlPath, StandardCharsets.UTF_8);
            } else {
                ClassPathResource resource = new ClassPathResource("lebenslauf-filled.html");
                if (!resource.exists()) {
                    logger.warn("No HTML source for OpenHTML PDF (no file and no default template)");
                    return false;
                }
                try (InputStream in = resource.getInputStream()) {
                    html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to read HTML for OpenHTML PDF: {}", e.getMessage());
            return false;
        }

        try (InputStream fontStream = getClass().getResourceAsStream("/fonts/LiberationSans-Regular.ttf")) {
            if (fontStream != null) {
                byte[] fontBytes = fontStream.readAllBytes();
                String fontBase64 = Base64.getEncoder().encodeToString(fontBytes);
                String dataUri = "data:font/ttf;base64," + fontBase64;
                html = html.replace("url('../fonts/LiberationSans-Regular.ttf')", "url('" + dataUri + "')");
                html = html.replace("url(\"../fonts/LiberationSans-Regular.ttf\")", "url(\"" + dataUri + "\")");
            }
        } catch (IOException e) {
            logger.warn("Could not embed font for OpenHTML PDF: {}", e.getMessage());
        }

        try {
            org.jsoup.nodes.Document jsoupDoc = Jsoup.parse(html, "UTF-8");
            jsoupDoc.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.html);
            W3CDom w3cDom = new W3CDom();
            Document w3cDoc = w3cDom.fromJsoup(jsoupDoc);

            try (java.io.OutputStream os = Files.newOutputStream(outputPdfPath)) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode();
                builder.withW3cDocument(w3cDoc, "file:///");
                builder.toStream(os);
                builder.run();
            }
            logger.info("Lebenslauf PDF generated via OpenHTML to PDF: {}", outputPdfPath);
            return true;
        } catch (Exception e) {
            logger.warn("OpenHTML to PDF failed: {}", e.getMessage(), e);
            return false;
        }
    }

    private String extractSurnameFromLebenslaufHtml(Path htmlPath) {
        try {
            String html = Files.readString(htmlPath, StandardCharsets.UTF_8);
            String fullName = extractFullName(html);

            if (fullName == null || fullName.isBlank()) {
                return "LEBENSLAUF";
            }

            String[] parts = fullName.trim().split("\\s+");
            String rawSurname = parts[parts.length - 1];
            String sanitized = sanitizeFilenamePart(rawSurname);
            return sanitized.isBlank() ? "LEBENSLAUF" : sanitized.toUpperCase(Locale.ROOT);
        } catch (IOException e) {
            logger.warn("Failed to extract surname from lebenslauf HTML {}", htmlPath, e);
            return "LEBENSLAUF";
        }
    }

    private String extractFullName(String html) {
        Matcher titleMatcher = TITLE_NAME_PATTERN.matcher(html);
        if (titleMatcher.find()) {
            return titleMatcher.group(1).trim();
        }

        Matcher nameBlockMatcher = NAME_BLOCK_PATTERN.matcher(html);
        if (nameBlockMatcher.find()) {
            String raw = HTML_TAGS_PATTERN.matcher(nameBlockMatcher.group(1)).replaceAll(" ");
            return raw.replaceAll("\\s+", " ").trim();
        }

        return "";
    }

    private String sanitizeFilenamePart(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return NON_ASCII_FILENAME_PATTERN.matcher(normalized).replaceAll("");
    }

    private String convertBiographyToText(java.util.Map<String, Object> biography) {
        // Convert biography Map to JSON string for storage
        return gson.toJson(biography);
    }
}

