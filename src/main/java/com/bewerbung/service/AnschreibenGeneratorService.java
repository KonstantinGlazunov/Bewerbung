package com.bewerbung.service;

import com.bewerbung.model.JobRequirements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AnschreibenGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(AnschreibenGeneratorService.class);
    
    private final GeminiAiService geminiAiService;

    public AnschreibenGeneratorService(GeminiAiService geminiAiService) {
        this.geminiAiService = geminiAiService;
    }

    public String generateAnschreiben(JobRequirements job, String candidateName) {
        logger.info("Generating Bewerbungsanschreiben for candidate: {} and position: {}", 
                candidateName, job.getPosition());
        
        String prompt = buildPrompt(job, candidateName);
        logger.debug("Generated prompt for Anschreiben");
        
        String anschreiben = geminiAiService.generateText(prompt);
        
        logger.info("Successfully generated Bewerbungsanschreiben (length: {} characters)", 
                anschreiben.length());
        
        return anschreiben;
    }

    private String buildPrompt(JobRequirements job, String candidateName) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Schreibe ein professionelles Bewerbungsanschreiben auf Deutsch für eine Bewerbung. ");
        prompt.append("Das Anschreiben soll formal, überzeugend und gut strukturiert sein.\n\n");
        
        prompt.append("Kandidatenname: ").append(candidateName).append("\n\n");
        
        prompt.append("Stellenausschreibung Details:\n");
        prompt.append("- Position: ").append(job.getPosition()).append("\n");
        prompt.append("- Unternehmen: ").append(job.getCompany()).append("\n");
        prompt.append("- Standort: ").append(job.getLocation()).append("\n");
        
        if (job.getRequiredSkills() != null && !job.getRequiredSkills().isEmpty()) {
            prompt.append("- Erforderliche Kenntnisse: ").append(String.join(", ", job.getRequiredSkills())).append("\n");
        }
        
        if (job.getPreferredSkills() != null && !job.getPreferredSkills().isEmpty()) {
            prompt.append("- Wünschenswerte Kenntnisse: ").append(String.join(", ", job.getPreferredSkills())).append("\n");
        }
        
        if (job.getEducation() != null && !job.getEducation().isEmpty()) {
            prompt.append("- Anforderungen an die Ausbildung: ").append(job.getEducation()).append("\n");
        }
        
        if (job.getExperience() != null && !job.getExperience().isEmpty()) {
            prompt.append("- Berufserfahrung: ").append(job.getExperience()).append("\n");
        }
        
        prompt.append("\n");
        prompt.append("Bitte erstelle ein vollständiges Bewerbungsanschreiben mit:\n");
        prompt.append("1. Korrekter Anrede\n");
        prompt.append("2. Einleitung, die Interesse weckt\n");
        prompt.append("3. Hauptteil mit relevanten Qualifikationen und Erfahrungen\n");
        prompt.append("4. Schluss mit Bitte um Einladung zum Vorstellungsgespräch\n");
        prompt.append("5. Professionelle Grußformel\n\n");
        prompt.append("Das Anschreiben soll etwa eine Seite lang sein und direkt ansprechen, warum der Kandidat für diese Position geeignet ist.");
        
        return prompt.toString();
    }
}

