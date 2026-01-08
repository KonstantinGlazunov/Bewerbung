package com.bewerbung.runner;

import com.bewerbung.model.Biography;
import com.bewerbung.model.JobRequirements;
import com.bewerbung.service.AnschreibenGeneratorService;
import com.bewerbung.service.BiographyService;
import com.bewerbung.service.LebenslaufGeneratorService;
import com.bewerbung.service.VacancyAnalyzerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class VacancyAnalysisRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(VacancyAnalysisRunner.class);
    
    private final VacancyAnalyzerService vacancyAnalyzerService;
    private final AnschreibenGeneratorService anschreibenGeneratorService;
    private final BiographyService biographyService;
    private final LebenslaufGeneratorService lebenslaufGeneratorService;

    public VacancyAnalysisRunner(VacancyAnalyzerService vacancyAnalyzerService,
                                 AnschreibenGeneratorService anschreibenGeneratorService,
                                 BiographyService biographyService,
                                 LebenslaufGeneratorService lebenslaufGeneratorService) {
        this.vacancyAnalyzerService = vacancyAnalyzerService;
        this.anschreibenGeneratorService = anschreibenGeneratorService;
        this.biographyService = biographyService;
        this.lebenslaufGeneratorService = lebenslaufGeneratorService;
    }

    @Override
    public void run(ApplicationArguments args) {
        logger.info("Application started - Running vacancy analysis...");
        
        try {
            JobRequirements jobRequirements = vacancyAnalyzerService.analyzeVacancy();
            logger.info("Vacancy analysis completed successfully");
            
            logger.info("Loading biography data...");
            Biography biography = biographyService.loadBiography();
            
            logger.info("Generating Bewerbungsanschreiben...");
            String anschreiben = anschreibenGeneratorService.generateAnschreiben(jobRequirements, biography);
            
            logger.info("=== Generated Bewerbungsanschreiben ===");
            logger.info("\n{}", anschreiben);
            logger.info("=======================================");
            
            logger.info("Generating Lebenslauf...");
            String lebenslauf = lebenslaufGeneratorService.generateLebenslauf(biography);
            
            logger.info("=== Generated Lebenslauf ===");
            logger.info("\n{}", lebenslauf);
            logger.info("============================");
            
        } catch (Exception e) {
            logger.error("Error during vacancy analysis, Anschreiben generation, or Lebenslauf generation on startup", e);
        }
    }
}

