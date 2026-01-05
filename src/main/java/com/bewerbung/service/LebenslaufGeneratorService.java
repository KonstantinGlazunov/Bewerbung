package com.bewerbung.service;

import com.bewerbung.model.Biography;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LebenslaufGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(LebenslaufGeneratorService.class);
    
    @Autowired
    private GroqAiService groqAiService;

    public String generateLebenslauf(Biography bio) {
        logger.info("Generating Lebenslauf for: {}", bio.getName());
        
        String prompt = buildPrompt(bio);
        logger.debug("Generated prompt for Lebenslauf");
        
        String lebenslauf = groqAiService.generateText(prompt);
        
        logger.info("Successfully generated Lebenslauf (length: {} characters)", 
                lebenslauf.length());
        
        return lebenslauf;
    }

    private String buildPrompt(Biography bio) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Erstelle einen professionellen Lebenslauf (CV) auf Deutsch basierend auf den folgenden Informationen. ");
        prompt.append("Der Lebenslauf soll strukturiert, übersichtlich und professionell formatiert sein.\n\n");
        
        prompt.append("=== Persönliche Daten ===\n");
        prompt.append("Name: ").append(bio.getName()).append("\n");
        if (bio.getEmail() != null && !bio.getEmail().isEmpty()) {
            prompt.append("E-Mail: ").append(bio.getEmail()).append("\n");
        }
        if (bio.getPhone() != null && !bio.getPhone().isEmpty()) {
            prompt.append("Telefon: ").append(bio.getPhone()).append("\n");
        }
        if (bio.getAddress() != null && !bio.getAddress().isEmpty()) {
            prompt.append("Adresse: ").append(bio.getAddress()).append("\n");
        }
        if (bio.getDateOfBirth() != null && !bio.getDateOfBirth().isEmpty()) {
            prompt.append("Geburtsdatum: ").append(bio.getDateOfBirth()).append("\n");
        }
        if (bio.getNationality() != null && !bio.getNationality().isEmpty()) {
            prompt.append("Nationalität: ").append(bio.getNationality()).append("\n");
        }
        prompt.append("\n");
        
        if (bio.getWorkExperience() != null && !bio.getWorkExperience().isEmpty()) {
            prompt.append("=== Berufserfahrung ===\n");
            for (Biography.WorkExperience workExp : bio.getWorkExperience()) {
                prompt.append("Position: ").append(workExp.getPosition()).append("\n");
                prompt.append("Unternehmen: ").append(workExp.getCompany()).append("\n");
                if (workExp.getLocation() != null && !workExp.getLocation().isEmpty()) {
                    prompt.append("Ort: ").append(workExp.getLocation()).append("\n");
                }
                prompt.append("Zeitraum: ").append(workExp.getStartDate())
                        .append(" - ").append(workExp.getEndDate()).append("\n");
                if (workExp.getResponsibilities() != null && !workExp.getResponsibilities().isEmpty()) {
                    prompt.append("Aufgaben: ").append(workExp.getResponsibilities()).append("\n");
                }
                prompt.append("\n");
            }
        }
        
        if (bio.getEducation() != null && !bio.getEducation().isEmpty()) {
            prompt.append("=== Ausbildung ===\n");
            for (Biography.Education edu : bio.getEducation()) {
                prompt.append("Abschluss: ").append(edu.getDegree()).append("\n");
                prompt.append("Institution: ").append(edu.getInstitution()).append("\n");
                if (edu.getLocation() != null && !edu.getLocation().isEmpty()) {
                    prompt.append("Ort: ").append(edu.getLocation()).append("\n");
                }
                prompt.append("Zeitraum: ").append(edu.getStartDate())
                        .append(" - ").append(edu.getEndDate()).append("\n");
                if (edu.getDescription() != null && !edu.getDescription().isEmpty()) {
                    prompt.append("Beschreibung: ").append(edu.getDescription()).append("\n");
                }
                prompt.append("\n");
            }
        }
        
        if (bio.getTechnicalSkills() != null && !bio.getTechnicalSkills().isEmpty()) {
            prompt.append("=== Fähigkeiten ===\n");
            prompt.append("Technische Fähigkeiten: ").append(String.join(", ", bio.getTechnicalSkills())).append("\n");
            prompt.append("\n");
        }
        
        if (bio.getSoftSkills() != null && !bio.getSoftSkills().isEmpty()) {
            prompt.append("Soft Skills: ").append(String.join(", ", bio.getSoftSkills())).append("\n");
            prompt.append("\n");
        }
        
        if (bio.getLanguages() != null && !bio.getLanguages().isEmpty()) {
            prompt.append("=== Sprachen ===\n");
            for (Biography.Language lang : bio.getLanguages()) {
                prompt.append(lang.getLanguage()).append(": ").append(lang.getLevel()).append("\n");
            }
            prompt.append("\n");
        }
        
        if (bio.getCertifications() != null && !bio.getCertifications().isEmpty()) {
            prompt.append("=== Zertifikate ===\n");
            for (Biography.Certification cert : bio.getCertifications()) {
                prompt.append("Zertifikat: ").append(cert.getName()).append("\n");
                prompt.append("Aussteller: ").append(cert.getIssuer()).append("\n");
                prompt.append("Datum: ").append(cert.getDate()).append("\n");
                prompt.append("\n");
            }
        }
        
        prompt.append("\n");
        prompt.append("Bitte erstelle einen vollständigen, professionellen Lebenslauf mit folgenden Abschnitten:\n");
        prompt.append("1. Persönliche Daten (Name, Kontaktinformationen, Geburtsdatum, Nationalität)\n");
        prompt.append("2. Berufserfahrung (chronologisch, neueste zuerst, mit Position, Unternehmen, Zeitraum und Aufgaben)\n");
        prompt.append("3. Ausbildung (chronologisch, neueste zuerst, mit Abschluss, Institution, Zeitraum)\n");
        prompt.append("4. Fähigkeiten (technische Fähigkeiten und Soft Skills)\n");
        prompt.append("5. Sprachen (mit Niveau)\n");
        prompt.append("6. Zertifikate (falls vorhanden)\n\n");
        prompt.append("Der Lebenslauf soll professionell formatiert sein, übersichtlich strukturiert und alle relevanten Informationen enthalten. ");
        prompt.append("Verwende eine klare, prägnante Sprache und achte auf korrekte deutsche Rechtschreibung.");
        
        return prompt.toString();
    }
}

