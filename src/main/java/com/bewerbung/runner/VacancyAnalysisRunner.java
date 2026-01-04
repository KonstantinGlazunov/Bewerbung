package com.bewerbung.runner;

import com.bewerbung.model.JobRequirements;
import com.bewerbung.service.AnschreibenGeneratorService;
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

    public VacancyAnalysisRunner(VacancyAnalyzerService vacancyAnalyzerService,
                                 AnschreibenGeneratorService anschreibenGeneratorService) {
        this.vacancyAnalyzerService = vacancyAnalyzerService;
        this.anschreibenGeneratorService = anschreibenGeneratorService;
    }

    @Override
    public void run(ApplicationArguments args) {
        logger.info("Application started - Running vacancy analysis...");
        
        try {
            JobRequirements jobRequirements = vacancyAnalyzerService.analyzeVacancy();
            logger.info("Vacancy analysis completed successfully");
            
            logger.info("Generating Bewerbungsanschreiben...");
            String candidateName = "Max Mustermann";
            String anschreiben = anschreibenGeneratorService.generateAnschreiben(jobRequirements, candidateName);
            
            logger.info("=== Generated Bewerbungsanschreiben ===");
            logger.info("\n{}", anschreiben);
            logger.info("=======================================");
            
        } catch (Exception e) {
            logger.error("Error during vacancy analysis or Anschreiben generation on startup", e);
        }
    }
}

