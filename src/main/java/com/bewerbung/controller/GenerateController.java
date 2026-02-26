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
import org.springframework.beans.factory.annotation.Value;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.InputStream;
import java.util.Base64;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
    /** One-time tokens for Chrome PDF: when Chrome loads lebenslauf/html it has no session cookie; token maps to sessionId. */
    private static final ConcurrentHashMap<String, String> PDF_SESSION_TOKENS = new ConcurrentHashMap<>();

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

    @Value("${pdf.lebenslauf.use-wkhtmltopdf:false}")
    private boolean pdfUseWkhtmltopdfByDefault;

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

    private static String safeSessionId(String sessionId) {
        return sessionId == null ? "" : sessionId.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    @PostMapping("/upload-photo")
    public ResponseEntity<Map<String, String>> uploadResumePhoto(HttpServletRequest request,
                                                                  @RequestParam("photo") MultipartFile photo) {
        logger.info("Received CV photo upload request");
        String sessionId = request.getSession(true).getId();
        String photoId = tempPhotoStorageService.saveTemporaryPhoto(sessionId, photo);
        return ResponseEntity.ok(Map.of(
                "message", "Photo uploaded successfully",
                "photoId", photoId
        ));
    }

    @PostMapping
    public ResponseEntity<?> generate(HttpServletRequest request, @Valid @RequestBody GenerateRequestDto dto) {
        logger.info("Received generate request");
        String sessionId = request.getSession(true).getId();
        GenerateRequestDto req = dto;

        if (req.getBiography().isEmpty()) {
            throw new IllegalArgumentException("Biography must not be empty");
        }

        String cvText = convertBiographyToText(req.getBiography());
        String vacancyText = req.getJobPosting();

        logger.info("Data received - Vacancy: {} chars, CV (biography): {} chars, Wishes: {} chars",
            vacancyText != null ? vacancyText.length() : 0,
            cvText != null ? cvText.length() : 0,
            req.getWishes() != null ? req.getWishes().length() : 0);

        if (fileOutputService.isDefaultData(vacancyText, cvText) && (req.getWishes() == null || req.getWishes().trim().isEmpty())) {
            logger.info("Data matches default samples - using sample cover letter without AI processing");
            String sampleCoverLetter = fileOutputService.loadSampleCoverLetter();
            if (sampleCoverLetter != null && !sampleCoverLetter.trim().isEmpty()) {
                fileOutputService.writeAnschreiben(sessionId, sampleCoverLetter);
                String dataAnschreibenPath = "data/" + safeSessionId(sessionId) + "/anschreiben.txt";
                fileOutputService.writeAnschreiben(sessionId, sampleCoverLetter, dataAnschreibenPath);
                changeDetectionService.saveAnschreibenPath(sessionId, dataAnschreibenPath);
                try {
                    String biographyJsonString = gson.toJson(req.getBiography());
                    JsonObject biographyJson = gson.fromJson(biographyJsonString, JsonObject.class);
                    String lebenslaufHtml = lebenslaufTemplateService.generateLebenslauf(sessionId, biographyJson);
                    fileOutputService.writeLebenslauf(sessionId, lebenslaufHtml);
                    logger.info("Lebenslauf regenerated for default data flow");
                } catch (Exception e) {
                    logger.error("Failed to generate lebenslauf in default-data flow", e);
                    throw new RuntimeException("Failed to generate lebenslauf for default data", e);
                }
                logger.info("Sample cover letter loaded and saved to output files ({} chars)", sampleCoverLetter.length());
                return ResponseEntity.ok("Using sample cover letter (default data detected, AI skipped).");
            } else {
                logger.warn("Sample cover letter not found, falling back to AI generation");
            }
        }

        if (vacancyText != null && !vacancyText.isEmpty()) {
            String vacancyPreview = vacancyText.length() > 200 ? vacancyText.substring(0, 200) + "..." : vacancyText;
            logger.debug("Vacancy preview: {}", vacancyPreview.replace("\n", "\\n"));
        }
        if (cvText != null && !cvText.isEmpty()) {
            String cvPreview = cvText.length() > 200 ? cvText.substring(0, 200) + "..." : cvText;
            logger.debug("CV preview: {}", cvPreview.replace("\n", "\\n"));
        }

        String biographyJsonString = gson.toJson(req.getBiography());
        JsonObject biographyJson = gson.fromJson(biographyJsonString, JsonObject.class);
        Biography biography = biographyService.parseBiographyFromJson(biographyJson);

        ChangeDetectionService.ChangeResult changeResult = changeDetectionService.checkAndSave(sessionId, vacancyText, cvText, req.getWishes());

        if (!changeResult.hasChanges()) {
            logger.info("No changes detected. Skipping AI processing for anschreiben.");
            try {
                logger.info("Generating lebenslauf from biography data (no changes detected, but generating CV)");
                String lebenslaufHtml = lebenslaufTemplateService.generateLebenslauf(sessionId, biographyJson);
                fileOutputService.writeLebenslauf(sessionId, lebenslaufHtml);
                logger.info("Lebenslauf generated and saved successfully");
            } catch (Exception e) {
                logger.error("Error generating lebenslauf, continuing without it", e);
            }
            return ResponseEntity.ok("No changes detected. Existing analysis is still valid. AI processing skipped to save tokens. Lebenslauf generated.");
        }

        logger.info("Changes detected: {}", changeResult.getDescription());

        JobRequirements jobRequirements = null;
        if (changeResult.isVacancyChanged()) {
            jobRequirements = vacancyAnalyzerService.analyzeVacancy(req.getJobPosting());
            fileOutputService.writeAnalysis(sessionId, jobRequirements);
        } else {
            logger.info("Vacancy unchanged, skipping analysis");
        }

        if (changeResult.isVacancyChanged() || changeResult.isCvChanged() || changeResult.isWishesChanged()) {
            String coverLetter;
            if (changeResult.isWishesChanged() && !changeResult.isVacancyChanged() && !changeResult.isCvChanged()) {
                boolean hasFactExclusion = anschreibenGeneratorService.containsFactExclusion(req.getWishes());
                if (hasFactExclusion) {
                    logger.info("Wishes contain FACT_EXCLUSION (deletions) - must regenerate letter from scratch using all fields");
                    if (jobRequirements == null) {
                        jobRequirements = vacancyAnalyzerService.analyzeVacancy(req.getJobPosting());
                    }
                    coverLetter = anschreibenGeneratorService.generateAnschreiben(
                            jobRequirements, biography, req.getJobPosting(), req.getWishes());
                } else {
                    logger.info("Only wishes changed (no deletions) - attempting to apply corrections to existing anschreiben...");
                    String savedAnschreibenPath = changeDetectionService.getSavedAnschreibenPath(sessionId);
                    String existingAnschreiben = fileOutputService.readAnschreiben(sessionId);
                    if (existingAnschreiben == null && savedAnschreibenPath != null && !savedAnschreibenPath.isEmpty()) {
                        existingAnschreiben = fileOutputService.readAnschreiben(sessionId, savedAnschreibenPath);
                    }
                    if (existingAnschreiben == null || existingAnschreiben.trim().isEmpty()) {
                        existingAnschreiben = fileOutputService.readAnschreiben(sessionId, "output/anschreiben.md");
                    }
                    if (existingAnschreiben != null && !existingAnschreiben.trim().isEmpty()) {
                        logger.info("Found existing anschreiben (length: {} chars), applying corrections...", existingAnschreiben.length());
                        String language = req.getLanguage();
                        if (language == null || language.trim().isEmpty()) language = "de";
                        if (jobRequirements == null) {
                            jobRequirements = vacancyAnalyzerService.analyzeVacancy(req.getJobPosting());
                        }
                        coverLetter = anschreibenGeneratorService.applyCorrectionsToAnschreiben(
                                existingAnschreiben, req.getWishes(), language,
                                jobRequirements, biography, req.getJobPosting());
                        logger.info("Corrections applied successfully (length: {} chars)", coverLetter.length());
                    } else {
                        logger.warn("No existing anschreiben found, falling back to full generation");
                        if (jobRequirements == null) {
                            jobRequirements = vacancyAnalyzerService.analyzeVacancy(req.getJobPosting());
                        }
                        coverLetter = anschreibenGeneratorService.generateAnschreiben(
                                jobRequirements, biography, req.getJobPosting(), req.getWishes());
                    }
                }
            } else {
                if (jobRequirements == null) {
                    jobRequirements = vacancyAnalyzerService.analyzeVacancy(req.getJobPosting());
                }
                logger.info("Generating anschreiben from scratch...");
                coverLetter = anschreibenGeneratorService.generateAnschreiben(
                        jobRequirements, biography, req.getJobPosting(), req.getWishes());
            }
            logger.info("Anschreiben ready (length: {} chars), writing to file...", coverLetter.length());
            fileOutputService.writeAnschreiben(sessionId, coverLetter);
            String dataAnschreibenPath = "data/" + safeSessionId(sessionId) + "/anschreiben.txt";
            fileOutputService.writeAnschreiben(sessionId, coverLetter, dataAnschreibenPath);
            changeDetectionService.saveAnschreibenPath(sessionId, dataAnschreibenPath);
            logger.info("Anschreiben file write completed");
        } else {
            logger.info("No changes detected, skipping anschreiben generation");
        }

        try {
            logger.info("Generating lebenslauf from biography data");
            String lebenslaufHtml = lebenslaufTemplateService.generateLebenslauf(sessionId, biographyJson);
            fileOutputService.writeLebenslauf(sessionId, lebenslaufHtml);
            logger.info("Lebenslauf generated and saved successfully");
        } catch (Exception e) {
            logger.error("Error generating lebenslauf, continuing without it", e);
        }

        fileOutputService.writeNotes(sessionId, changeResult.getDescription(),
            changeResult.isVacancyChanged(), changeResult.isCvChanged());

        logger.info("Processing complete. Results written for session.");
        return ResponseEntity.ok("Processing complete. Results written to session storage.");
    }

    @PostMapping("/cover-letter")
    public ResponseEntity<?> generateCoverLetter(HttpServletRequest request, @Valid @RequestBody GenerateRequestDto dto) {
        logger.info("Received cover letter generation request");
        String sessionId = request.getSession(true).getId();
        GenerateRequestDto req = dto;
        if (req.getBiography().isEmpty()) {
            throw new IllegalArgumentException("Biography must not be empty");
        }

        // Convert biography Map to text for storage
        String cvText = convertBiographyToText(req.getBiography());
        String vacancyText = req.getJobPosting();

        // Default to German if language not specified
        String language = req.getLanguage();
        if (language == null || language.trim().isEmpty()) {
            language = "de";
        }

        // Convert biography Map to JsonObject for processing (needed for both anschreiben and lebenslauf)
        String biographyJsonString = gson.toJson(req.getBiography());
        JsonObject biographyJson = gson.fromJson(biographyJsonString, JsonObject.class);
        Biography biography = biographyService.parseBiographyFromJson(biographyJson);

        ChangeDetectionService.ChangeResult changeResult = changeDetectionService.checkAndSave(sessionId, vacancyText, cvText, req.getWishes(), language);

        // If no changes detected, try to load saved Anschreiben
        if (!changeResult.hasChanges()) {
            logger.info("No changes detected. Attempting to load saved Anschreiben...");
            String savedAnschreibenPath = changeDetectionService.getSavedAnschreibenPath(sessionId);
            String savedAnschreiben = fileOutputService.readAnschreiben(sessionId);
            if ((savedAnschreiben == null || savedAnschreiben.trim().isEmpty()) && savedAnschreibenPath != null && !savedAnschreibenPath.isEmpty()) {
                savedAnschreiben = fileOutputService.readAnschreiben(sessionId, savedAnschreibenPath);
            }
            if (savedAnschreiben != null && !savedAnschreiben.trim().isEmpty()) {
                    logger.info("Successfully loaded saved Anschreiben from session");
                    fileOutputService.writeAnschreiben(sessionId, savedAnschreiben);
                    try {
                        logger.info("Generating lebenslauf from biography data (no changes detected, but generating CV)");
                        String lebenslaufHtml = lebenslaufTemplateService.generateLebenslauf(sessionId, biographyJson);
                        fileOutputService.writeLebenslauf(sessionId, lebenslaufHtml);
                        logger.info("Lebenslauf generated and saved successfully");
                    } catch (Exception e) {
                        logger.error("Error generating lebenslauf, continuing without it", e);
                    }
                    
                    return ResponseEntity.ok("No changes detected. Using saved Anschreiben. Lebenslauf generated.");
            }
            logger.info("No saved Anschreiben found. Skipping AI processing.");
            
            // Still generate lebenslauf even if no changes detected
            try {
                logger.info("Generating lebenslauf from biography data (no changes detected, but generating CV)");
                String lebenslaufHtml = lebenslaufTemplateService.generateLebenslauf(sessionId, biographyJson);
                fileOutputService.writeLebenslauf(sessionId, lebenslaufHtml);
                logger.info("Lebenslauf generated and saved successfully");
            } catch (Exception e) {
                logger.error("Error generating lebenslauf, continuing without it", e);
            }
            return ResponseEntity.ok("No changes detected. Existing analysis is still valid. AI processing skipped to save tokens. Lebenslauf generated.");
        }

        logger.info("Changes detected: {}", changeResult.getDescription());
        JobRequirements jobRequirements = vacancyAnalyzerService.analyzeVacancy(req.getJobPosting());
        if (changeResult.isVacancyChanged()) {
            fileOutputService.writeAnalysis(sessionId, jobRequirements);
        }

        String coverLetter;
        if (changeResult.isWishesChanged() && !changeResult.isVacancyChanged() && !changeResult.isCvChanged() && !changeResult.isLanguageChanged()) {
            boolean hasFactExclusion = anschreibenGeneratorService.containsFactExclusion(req.getWishes());
            if (hasFactExclusion) {
                logger.info("Wishes contain FACT_EXCLUSION (deletions) - must regenerate letter from scratch using all fields");
                coverLetter = anschreibenGeneratorService.generateAnschreiben(
                        jobRequirements, biography, req.getJobPosting(), req.getWishes(), language);
            } else {
                logger.info("Only wishes changed (no deletions, language unchanged) - attempting to apply corrections to existing anschreiben...");
                String savedAnschreibenPath = changeDetectionService.getSavedAnschreibenPath(sessionId);
                String existingAnschreiben = fileOutputService.readAnschreiben(sessionId);
                if (existingAnschreiben == null && savedAnschreibenPath != null && !savedAnschreibenPath.isEmpty()) {
                    existingAnschreiben = fileOutputService.readAnschreiben(sessionId, savedAnschreibenPath);
                }
                if (existingAnschreiben == null || existingAnschreiben.trim().isEmpty()) {
                    existingAnschreiben = fileOutputService.readAnschreiben(sessionId, "output/anschreiben.md");
                }
                if (existingAnschreiben != null && !existingAnschreiben.trim().isEmpty()) {
                    logger.info("Found existing anschreiben (length: {} chars), applying corrections...", existingAnschreiben.length());
                    coverLetter = anschreibenGeneratorService.applyCorrectionsToAnschreiben(
                            existingAnschreiben, req.getWishes(), language,
                            jobRequirements, biography, req.getJobPosting());
                    logger.info("Corrections applied successfully (length: {} chars)", coverLetter.length());
                } else {
                    logger.warn("No existing anschreiben found, falling back to full generation");
                    coverLetter = anschreibenGeneratorService.generateAnschreiben(
                            jobRequirements, biography, req.getJobPosting(), req.getWishes(), language);
                }
            }
        } else {
            if (changeResult.isLanguageChanged()) {
                logger.info("Language changed - must regenerate cover letter completely");
            }
            logger.info("Generating anschreiben from scratch...");
            coverLetter = anschreibenGeneratorService.generateAnschreiben(
                    jobRequirements, biography, req.getJobPosting(), req.getWishes(), language);
        }

        String dataAnschreibenPath = "data/" + safeSessionId(sessionId) + "/anschreiben.txt";
        fileOutputService.writeAnschreiben(sessionId, coverLetter);
        fileOutputService.writeAnschreiben(sessionId, coverLetter, dataAnschreibenPath);
        changeDetectionService.saveAnschreibenPath(sessionId, dataAnschreibenPath);

        try {
            String biographyJsonStr = gson.toJson(req.getBiography());
            JsonObject biographyJsonForLebenslauf = gson.fromJson(biographyJsonStr, JsonObject.class);
            logger.info("Generating lebenslauf from biography data");
            String lebenslaufHtml = lebenslaufTemplateService.generateLebenslauf(sessionId, biographyJsonForLebenslauf);
            fileOutputService.writeLebenslauf(sessionId, lebenslaufHtml);
            logger.info("Lebenslauf generated and saved successfully");
        } catch (Exception e) {
            logger.error("Error generating lebenslauf, continuing without it", e);
        }

        fileOutputService.writeNotes(sessionId, changeResult.getDescription(),
            changeResult.isVacancyChanged(), changeResult.isCvChanged());
        logger.info("Cover letter generated and written to output files");
        return ResponseEntity.ok("Cover letter generated and written to output files");
    }


    @PostMapping("/from-file")
    public ResponseEntity<GenerateResponseDto> generateFromFile(
            HttpServletRequest request,
            @RequestParam("biographyFile") MultipartFile biographyFile,
            @RequestParam("jobPosting") String jobPosting,
            @RequestParam(value = "wishes", required = false) String wishes,
            @RequestParam(value = "language", required = false) String language) {
        String sessionId = request.getSession(true).getId();
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
                String dataAnschreibenPath = "data/" + safeSessionId(sessionId) + "/anschreiben.txt";
                fileOutputService.writeAnschreiben(sessionId, sampleCoverLetter);
                fileOutputService.writeAnschreiben(sessionId, sampleCoverLetter, dataAnschreibenPath);
                changeDetectionService.saveAnschreibenPath(sessionId, dataAnschreibenPath);
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

        ChangeDetectionService.ChangeResult changeResult = changeDetectionService.checkAndSave(sessionId, jobPosting, biographyText, wishes, language);
        
        logger.info("Change detection result - hasChanges: {}, vacancyChanged: {}, cvChanged: {}, wishesChanged: {}, languageChanged: {}", 
            changeResult.hasChanges(), changeResult.isVacancyChanged(), changeResult.isCvChanged(), 
            changeResult.isWishesChanged(), changeResult.isLanguageChanged());

        // If no changes detected AND language hasn't changed, try to load saved Anschreiben
        // IMPORTANT: If language changed, we must regenerate even if other data hasn't changed
        if (!changeResult.hasChanges() && !changeResult.isLanguageChanged()) {
            logger.info("No changes detected and language hasn't changed. Attempting to load saved Anschreiben...");
            String savedAnschreibenPath = changeDetectionService.getSavedAnschreibenPath(sessionId);
            String savedAnschreiben = fileOutputService.readAnschreiben(sessionId);
            if (savedAnschreiben == null && savedAnschreibenPath != null && !savedAnschreibenPath.isEmpty()) {
                savedAnschreiben = fileOutputService.readAnschreiben(sessionId, savedAnschreibenPath);
            }
            if (savedAnschreiben != null && !savedAnschreiben.trim().isEmpty()) {
                    logger.info("Successfully loaded saved Anschreiben from session");
                    fileOutputService.writeAnschreiben(sessionId, savedAnschreiben);
                    GenerateResponseDto response = new GenerateResponseDto(savedAnschreiben);
                    return ResponseEntity.ok(response);
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
                String savedAnschreibenPath = changeDetectionService.getSavedAnschreibenPath(sessionId);
                String existingAnschreiben = fileOutputService.readAnschreiben(sessionId);
                if (existingAnschreiben == null && savedAnschreibenPath != null && !savedAnschreibenPath.isEmpty()) {
                    existingAnschreiben = fileOutputService.readAnschreiben(sessionId, savedAnschreibenPath);
                }
                if (existingAnschreiben == null || existingAnschreiben.trim().isEmpty()) {
                    existingAnschreiben = fileOutputService.readAnschreiben(sessionId, "output/anschreiben.md");
                }
                if (existingAnschreiben != null && !existingAnschreiben.trim().isEmpty()) {
                    logger.info("Found existing anschreiben (length: {} chars), applying corrections...", existingAnschreiben.length());
                    coverLetter = anschreibenGeneratorService.applyCorrectionsToAnschreiben(
                            existingAnschreiben, wishes, languageForGeneration,
                            jobRequirements, biography, jobPosting);
                    logger.info("Corrections applied successfully (length: {} chars)", coverLetter.length());
                } else {
                    logger.warn("No existing anschreiben found, falling back to full generation");
                    coverLetter = anschreibenGeneratorService.generateAnschreiben(
                            jobRequirements, biography, jobPosting, wishes, languageForGeneration);
                }
            }
        } else {
            if (changeResult.isLanguageChanged()) {
                logger.info("Language changed - must regenerate cover letter completely");
            }
            logger.info("Generating anschreiben from scratch...");
            coverLetter = anschreibenGeneratorService.generateAnschreiben(
                    jobRequirements, biography, jobPosting, wishes, languageForGeneration);
        }

        String dataAnschreibenPath = "data/" + safeSessionId(sessionId) + "/anschreiben.txt";
        fileOutputService.writeAnschreiben(sessionId, coverLetter);
        fileOutputService.writeAnschreiben(sessionId, coverLetter, dataAnschreibenPath);
        changeDetectionService.saveAnschreibenPath(sessionId, dataAnschreibenPath);

        try {
            logger.info("Generating lebenslauf from Biography object");
            String lebenslaufHtml = lebenslaufTemplateService.generateLebenslauf(sessionId, biography);
            fileOutputService.writeLebenslauf(sessionId, lebenslaufHtml);
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
    public ResponseEntity<String> getLebenslaufHtml(HttpServletRequest request,
            @RequestParam(name = "pdfToken", required = false) String pdfToken) {
        String sessionId = pdfToken != null && !pdfToken.isBlank()
                ? PDF_SESSION_TOKENS.remove(pdfToken)
                : request.getSession(true).getId();
        if (sessionId == null || sessionId.isBlank()) {
            throw new RuntimeException("Invalid or expired PDF token. Please request the PDF again.");
        }
        String html = fileOutputService.readLebenslauf(sessionId);
        if (html == null || html.isBlank()) {
            String cvText = fileOutputService.getCvText(sessionId);
            if (cvText != null && !cvText.isBlank()) {
                try {
                    JsonObject biographyJson = gson.fromJson(cvText.trim(), JsonObject.class);
                    if (biographyJson != null && (biographyJson.has("personalInformation") || biographyJson.has("name"))) {
                        logger.info("Generating Lebenslauf HTML from stored CV for session (html requested)");
                        html = lebenslaufTemplateService.generateLebenslauf(sessionId, biographyJson);
                        fileOutputService.writeLebenslauf(sessionId, html);
                    }
                } catch (Exception e) {
                    logger.debug("Could not generate Lebenslauf from stored CV: {}", e.getMessage());
                }
            }
            if (html == null || html.isBlank()) {
                throw new RuntimeException("Lebenslauf HTML not found for session. Please generate Lebenslauf first.");
            }
        }
        html = embedPhotoAsDataUri(html, sessionId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                .body(html);
    }

    @GetMapping("/lebenslauf/default-html")
    public ResponseEntity<String> getDefaultLebenslaufHtml(HttpServletRequest request) {
        try {
            ClassPathResource resource = new ClassPathResource("lebenslauf-filled.html");
            if (!resource.exists()) {
                throw new RuntimeException("Default lebenslauf HTML template not found in resources");
            }
            String html = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            html = html.replace("src=\"static/", "src=\"/static/");
            String sessionId = request.getSession(true).getId();
            html = embedPhotoAsDataUri(html, sessionId);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                    .body(html);
        } catch (IOException e) {
            logger.error("Failed to read default lebenslauf HTML template", e);
            throw new RuntimeException("Failed to read default lebenslauf HTML template", e);
        }
    }

    @GetMapping("/pdf/lebenslauf")
    public ResponseEntity<byte[]> generateLebenslaufPdf(
            HttpServletRequest request,
            @RequestParam(name = "defaultData", defaultValue = "false") boolean defaultData,
            @RequestParam(name = "force", defaultValue = "false") boolean forceRegenerate,
            @RequestParam(name = "useWkhtmltopdf", defaultValue = "false") boolean useWkhtmltopdf
    ) {
        String sessionId = request.getSession(true).getId();
        final String filename;
        final Path outputPdfPath;
        final String sourceUrl;
        String sessionHtml = null;

        if (defaultData) {
            filename = "Musterman_Lebenslauf.PDF";
            outputPdfPath = Paths.get("output", safeSessionId(sessionId), filename).toAbsolutePath();
            sourceUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
                    + "/api/generate/lebenslauf/default-html";
        } else {
            sessionHtml = fileOutputService.readLebenslauf(sessionId);
            if (sessionHtml == null || sessionHtml.isBlank()) {
                // Fallback: try to generate Lebenslauf HTML from stored CV (biography JSON) in this session
                String cvText = fileOutputService.getCvText(sessionId);
                if (cvText != null && !cvText.isBlank()) {
                    try {
                        JsonObject biographyJson = gson.fromJson(cvText.trim(), JsonObject.class);
                        if (biographyJson != null && (biographyJson.has("personalInformation") || biographyJson.has("name"))) {
                            logger.info("Generating Lebenslauf HTML from stored CV for session (PDF requested without prior generate)");
                            sessionHtml = lebenslaufTemplateService.generateLebenslauf(sessionId, biographyJson);
                            fileOutputService.writeLebenslauf(sessionId, sessionHtml);
                        }
                    } catch (Exception e) {
                        logger.debug("Could not generate Lebenslauf from stored CV: {}", e.getMessage());
                    }
                }
                if (sessionHtml == null || sessionHtml.isBlank()) {
                    throw new RuntimeException("Lebenslauf HTML not found for session. Please generate Lebenslauf first.");
                }
            }
            String surname = extractSurnameFromHtmlContent(sessionHtml);
            filename = surname + "_LebensLauf.PDF";
            outputPdfPath = Paths.get("output", safeSessionId(sessionId), filename).toAbsolutePath();
            String pdfToken = UUID.randomUUID().toString();
            PDF_SESSION_TOKENS.put(pdfToken, sessionId);
            sourceUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
                    + "/api/generate/lebenslauf/html?pdfToken=" + pdfToken;
        }

        // Keep a lightweight content signature next to the PDF to skip expensive re-rendering
        // when the source HTML has not changed between requests.
        String contentSignature = defaultData
                ? calculateSha256(loadDefaultLebenslaufHtmlForSession(sessionId))
                : calculateSha256(sessionHtml != null ? sessionHtml : "");
        Path signaturePath = getPdfSignaturePath(outputPdfPath);

        // Check if PDF needs to be regenerated
        // Regenerate if:
        // 1) force flag is set
        // 2) PDF file does not exist / empty
        // 3) Source content signature differs from previously generated signature
        boolean needsRegeneration = false;
        if (forceRegenerate) {
            needsRegeneration = true;
            logger.info("Force regeneration requested, will regenerate PDF");
        } else {
            try {
                if (!Files.exists(outputPdfPath) || Files.size(outputPdfPath) == 0) {
                    needsRegeneration = true;
                    logger.debug("PDF file does not exist or is empty, will regenerate");
                } else {
                    String existingSignature = Files.exists(signaturePath)
                            ? Files.readString(signaturePath, StandardCharsets.UTF_8).trim()
                            : "";
                    if (!contentSignature.equals(existingSignature)) {
                        needsRegeneration = true;
                        logger.info("Lebenslauf content changed (or signature missing), will regenerate PDF");
                    } else {
                        logger.info("Lebenslauf content unchanged, using cached PDF");
                    }
                }
            } catch (IOException e) {
                logger.warn("Error checking PDF/signature, will regenerate PDF: {}", e.getMessage());
                needsRegeneration = true;
            }
        }

        if (needsRegeneration) {
            boolean useWkhtmltopdfNow = useWkhtmltopdf || pdfUseWkhtmltopdfByDefault;
            boolean generated;
            if (useWkhtmltopdfNow) {
                logger.info("Using wkhtmltopdf (requested or server default)");
                if (defaultData) {
                    generated = generateLebenslaufPdfWithWkhtmltopdfDefault(outputPdfPath, sessionId);
                } else {
                    generated = generateLebenslaufPdfWithWkhtmltopdf(sessionHtml, outputPdfPath, sessionId);
                }
            } else {
                generated = generateLebenslaufPdfWithChrome(sourceUrl, outputPdfPath);
                if (!generated && !defaultData && sessionHtml != null) {
                    logger.info("Chrome/Chromium not available, trying wkhtmltopdf (lightweight WebKit)");
                    generated = generateLebenslaufPdfWithWkhtmltopdf(sessionHtml, outputPdfPath, sessionId);
                }
            }
            if (!generated) {
                throw new RuntimeException("Failed to generate Lebenslauf PDF. Install google-chrome/chromium or wkhtmltopdf.");
            }
            try {
                Files.writeString(signaturePath, contentSignature, StandardCharsets.UTF_8);
            } catch (IOException e) {
                logger.warn("Failed to write PDF signature file {}: {}", signaturePath, e.getMessage());
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

    private Path getPdfSignaturePath(Path outputPdfPath) {
        return Paths.get(outputPdfPath.toString() + ".sha256");
    }

    private String calculateSha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String loadDefaultLebenslaufHtmlForSession(String sessionId) {
        try {
            ClassPathResource resource = new ClassPathResource("lebenslauf-filled.html");
            if (!resource.exists()) {
                return "default-template-missing";
            }
            String html = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            html = html.replace("src=\"static/", "src=\"/static/");
            return embedPhotoAsDataUri(html, sessionId);
        } catch (IOException e) {
            logger.warn("Failed to build default Lebenslauf HTML signature for session {}: {}", sessionId, e.getMessage());
            return "default-template-error";
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
                        "/usr/bin/google-chrome", "--headless", "--disable-gpu", "--no-sandbox",
                        "--print-to-pdf-no-header",
                        "--print-to-pdf=" + outputPdfPath.toString(),
                        sourceUrl
                ),
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

    private boolean generateLebenslaufPdfWithWkhtmltopdf(String htmlContent, Path outputPdfPath, String sessionId) {
        return runWkhtmltopdf(htmlContent, outputPdfPath, sessionId);
    }

    private boolean generateLebenslaufPdfWithWkhtmltopdfDefault(Path outputPdfPath, String sessionId) {
        try {
            ClassPathResource resource = new ClassPathResource("lebenslauf-filled.html");
            if (!resource.exists()) {
                logger.warn("Default lebenslauf HTML not found");
                return false;
            }
            String html = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            html = html.replace("src=\"static/", "src=\"/static/");
            return runWkhtmltopdf(html, outputPdfPath, sessionId);
        } catch (IOException e) {
            logger.warn("Failed to load default HTML for wkhtmltopdf: {}", e.getMessage());
            return false;
        }
    }

    private boolean runWkhtmltopdf(String html, Path outputPdfPath, String sessionId) {
        try {
            Path parent = outputPdfPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            logger.error("Failed to create output directory for PDF {}", outputPdfPath, e);
            return false;
        }

        try (InputStream fontStream = getClass().getResourceAsStream("/fonts/LiberationSans-Regular.ttf")) {
            if (fontStream != null) {
                byte[] fontBytes = fontStream.readAllBytes();
                String dataUri = "data:font/ttf;base64," + Base64.getEncoder().encodeToString(fontBytes);
                html = html.replace("url('../fonts/LiberationSans-Regular.ttf')", "url('" + dataUri + "')");
                html = html.replace("url(\"../fonts/LiberationSans-Regular.ttf\")", "url(\"" + dataUri + "\")");
            }
        } catch (IOException e) {
            logger.debug("Could not embed font for wkhtmltopdf: {}", e.getMessage());
        }

        html = embedPhotoAsDataUriForWkhtmltopdf(html, sessionId);
        html = replaceCssVariablesForWkhtmltopdf(html);
        html = injectWkhtmltopdfLayoutFallback(html);

        Path tempHtml = null;
        try {
            tempHtml = Files.createTempFile("lebenslauf_", ".html");
            Files.writeString(tempHtml, html, StandardCharsets.UTF_8);

            String uri = "file://" + tempHtml.toAbsolutePath().toString();
            Process process = new ProcessBuilder(
                    "wkhtmltopdf",
                    "--quiet",
                    "--page-size", "A4",
                    "--enable-local-file-access",
                    uri,
                    outputPdfPath.toAbsolutePath().toString()
            ).redirectErrorStream(true).start();

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logger.warn("wkhtmltopdf timed out");
                return false;
            }
            if (process.exitValue() != 0) {
                String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                logger.warn("wkhtmltopdf failed with exit {}: {}", process.exitValue(), out);
                return false;
            }
            if (!Files.exists(outputPdfPath) || Files.size(outputPdfPath) == 0) {
                return false;
            }
            logger.info("Lebenslauf PDF generated via wkhtmltopdf: {}", outputPdfPath);
            return true;
        } catch (Exception e) {
            logger.warn("wkhtmltopdf failed: {}", e.getMessage());
            return false;
        } finally {
            if (tempHtml != null) {
                try {
                    Files.deleteIfExists(tempHtml);
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Embeds the CV photo as a data URI only when the user has uploaded one.
     * If no photo is uploaded, the image is removed so the PDF shows empty space (no broken image).
     */
    private String embedPhotoAsDataUri(String html, String sessionId) {
        String dataUri = sessionId != null && !sessionId.isBlank()
                ? tempPhotoStorageService.getCurrentPhotoDataUri(sessionId).orElse("")
                : "";
        if (dataUri == null || dataUri.isEmpty()) {
            // No photo uploaded — remove img so we get empty place (no broken image)
            html = html.replaceFirst("\\s*<img\\s+[^>]*src=\"[^\"]*\"[^>]*/?>", " ");
            logger.debug("No photo uploaded: empty place in PDF");
            return html;
        }
        boolean changed = false;
        if (html.contains("src=\"/static/author-photo.jpg\"")) {
            html = html.replace("src=\"/static/author-photo.jpg\"", "src=\"" + dataUri + "\"");
            changed = true;
        }
        if (html.contains("src=\"static/author-photo.jpg\"")) {
            html = html.replace("src=\"static/author-photo.jpg\"", "src=\"" + dataUri + "\"");
            changed = true;
        }
        if (!changed) {
            html = html.replaceFirst("(<img\\s+[^>]*src=\")[^\"]*(\")", "$1" + dataUri + "$2");
        }
        logger.info("Photo embedded in HTML for PDF");
        return html;
    }

    private String embedPhotoAsDataUriForWkhtmltopdf(String html, String sessionId) {
        return embedPhotoAsDataUri(html, sessionId);
    }

    /** Replace CSS variables with fixed values so old WebKit (wkhtmltopdf) doesn't ignore rules. */
    private String replaceCssVariablesForWkhtmltopdf(String html) {
        html = html.replace("var(--header-bg)", "#e3e3e5");
        html = html.replace("var(--text)", "#3f434a");
        html = html.replace("var(--muted)", "#666b74");
        html = html.replace("var(--line)", "#8c8f95");
        html = html.replace("var(--accent)", "#40454e");
        html = html.replace("var(--font-primary)", "'Liberation Sans', 'Segoe UI', Arial, sans-serif");
        return html;
    }

    /**
     * Injects CSS overrides for wkhtmltopdf (old WebKit does not support CSS Grid/Flexbox).
     * Injected at end of first <style> so same block, our rules override.
     * Also neutralizes grid/flex in the block so the engine doesn't misapply them.
     */
    private String injectWkhtmltopdfLayoutFallback(String html) {
        int startStyle = html.indexOf("<style>");
        int endStyle = html.indexOf("</style>");
        if (startStyle == -1 || endStyle == -1 || endStyle <= startStyle) {
            return html;
        }
        String styleContent = html.substring(startStyle + 7, endStyle);
        // Neutralize Grid/Flex so old WebKit doesn't use them
        styleContent = styleContent.replace("display: grid", "display: block");
        styleContent = styleContent.replace("display: flex", "display: block");
        styleContent = styleContent.replaceAll("grid-template-columns:\\s*[^;]+;?", "");
        styleContent = styleContent.replaceAll("grid-template-areas:\\s*[^;]+;?", "");
        styleContent = styleContent.replace("align-items: center", "");
        styleContent = styleContent.replace("justify-self: center", "");

        String css = ""
            + "/* wkhtmltopdf fallback: no Grid - use table/float */"
            + ".page { width: 210mm !important; margin: 0 auto !important; background: #fff !important; overflow: hidden !important; }"
            + ".header-strip { display: table !important; width: 100% !important; height: 52.5mm !important; background: #e3e3e5 !important; padding: 0 14mm !important; table-layout: fixed !important; }"
            + ".header-strip .name-block { display: table-cell !important; width: 72% !important; vertical-align: middle !important; }"
            + ".header-strip .photo-wrap { display: table-cell !important; width: 28% !important; vertical-align: middle !important; text-align: center !important; }"
            + ".photo-wrap img { display: block !important; width: 46mm !important; height: 46mm !important; max-width: 100% !important; object-fit: cover !important; object-position: center !important; }"
            + ".content { overflow: hidden !important; }"
            + ".top-row { overflow: hidden !important; padding-bottom: 1mm !important; border-bottom: 1px solid #8c8f95 !important; }"
            + ".top-row .top-left { float: left !important; width: 38% !important; padding: 1.5mm 10mm 0 15mm !important; }"
            + ".top-row .top-right { float: right !important; width: 62% !important; padding: 1.5mm 15mm 0 1em !important; }"
            + ".content .bottom-left { float: left !important; clear: left !important; width: 38% !important; padding: 4mm 10mm 4mm 15mm !important; border-right: 1px solid #8c8f95 !important; min-height: 1px !important; }"
            + ".content .bottom-right { float: right !important; width: 62% !important; padding: 4mm 15mm 4mm 1em !important; min-height: 1px !important; }"
            + ".content::before { display: none !important; }"
            + ".contact li { overflow: hidden !important; }"
            + ".contact .icon { float: left !important; width: 6mm !important; }"
            + ".contact li span:last-child { display: block !important; margin-left: 8mm !important; }";

        return html.substring(0, startStyle + 7) + styleContent + css + html.substring(endStyle);
    }

    private String extractSurnameFromHtmlContent(String html) {
        if (html == null || html.isBlank()) return "LEBENSLAUF";
        String fullName = extractFullName(html);
        if (fullName == null || fullName.isBlank()) return "LEBENSLAUF";
        String[] parts = fullName.trim().split("\\s+");
        String rawSurname = parts[parts.length - 1];
        String sanitized = sanitizeFilenamePart(rawSurname);
        return sanitized.isBlank() ? "LEBENSLAUF" : sanitized.toUpperCase(Locale.ROOT);
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
