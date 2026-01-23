package com.bewerbung.runner;

import com.bewerbung.model.Biography;
import com.bewerbung.model.JobRequirements;
import com.bewerbung.service.AnschreibenGeneratorService;
import com.bewerbung.service.BiographyService;
import com.bewerbung.service.VacancyAnalyzerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Profile("local")
@Component
public class VacancyAnalysisRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(VacancyAnalysisRunner.class);
    
    private final VacancyAnalyzerService vacancyAnalyzerService;
    private final AnschreibenGeneratorService anschreibenGeneratorService;
    private final BiographyService biographyService;

    public VacancyAnalysisRunner(VacancyAnalyzerService vacancyAnalyzerService,
                                 AnschreibenGeneratorService anschreibenGeneratorService,
                                 BiographyService biographyService) {
        this.vacancyAnalyzerService = vacancyAnalyzerService;
        this.anschreibenGeneratorService = anschreibenGeneratorService;
        this.biographyService = biographyService;
    }

    @Override
    public void run(ApplicationArguments args) {
        logger.info("Application started - Running vacancy analysis...");
        
        try {
            // Get full vacancy text for better prompt generation
            String vacancyFullText = readVacancyFile();
            JobRequirements jobRequirements = vacancyAnalyzerService.analyzeVacancy(vacancyFullText);
            logger.info("Vacancy analysis completed successfully");
            
            logger.info("Loading biography data...");
            Biography biography = biographyService.loadBiography();
            
            logger.info("Generating Bewerbungsanschreiben...");
            String anschreiben = anschreibenGeneratorService.generateAnschreiben(jobRequirements, biography, vacancyFullText, null);
            
            logger.info("=== Generated Bewerbungsanschreiben ===");
            logger.info("\n{}", anschreiben);
            logger.info("=======================================");
            
        } catch (Exception e) {
            logger.error("Error during vacancy analysis or Anschreiben generation on startup", e);
        }
    }
    
    private String readVacancyFile() {
        try {
            org.springframework.core.io.ClassPathResource resource = 
                new org.springframework.core.io.ClassPathResource("input/job_posting.txt");
            @SuppressWarnings("null")
            String content = resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            return content;
        } catch (Exception e) {
            logger.warn("Could not read full vacancy text, will use structured data only", e);
            return null;
        }
    }
}

