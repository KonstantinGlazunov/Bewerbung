package com.bewerbung.service;

import com.bewerbung.model.Biography;
import com.bewerbung.model.JobRequirements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AnschreibenGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(AnschreibenGeneratorService.class);
    
    @Autowired
    private OpenAiService openAiService;

    public String generateAnschreiben(JobRequirements job, Biography biography) {
        logger.info("Generating Bewerbungsanschreiben for candidate: {} and position: {} at company: {}", 
                biography.getName(), job.getPosition(), job.getCompany());
        logger.info("Job location: {}, Required skills: {}", 
                job.getLocation(), 
                job.getRequiredSkills() != null ? String.join(", ", job.getRequiredSkills()) : "none");
        
        String prompt = buildPrompt(job, biography);
        logger.debug("Generated prompt for Anschreiben: {}", prompt);
        
        // Use HEAVY model for final document generation (higher quality)
        String anschreiben = openAiService.generateTextWithHeavyModel(prompt);
        
        logger.info("Successfully generated Bewerbungsanschreiben (length: {} characters)", 
                anschreiben.length());
        
        return anschreiben;
    }

    private String buildPrompt(JobRequirements job, Biography biography) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Schreibe ein professionelles Bewerbungsanschreiben auf Deutsch für eine Bewerbung.\n\n");
        
        // === CRITICAL RELEVANCE INSTRUCTIONS ===
        prompt.append("=== WICHTIGSTE REGEL: RELEVANZ ===\n");
        prompt.append("Das Anschreiben muss zur ausgeschriebenen Stelle PASSEN. ");
        prompt.append("Erwähne NUR Qualifikationen und Erfahrungen, die für die konkrete Position RELEVANT sind.\n\n");
        
        prompt.append("BEISPIELE für Relevanz:\n");
        prompt.append("- Für eine Fahrer-Stelle: Führerschein, Fahrerfahrung, Zuverlässigkeit, Ortskenntnisse - NICHT akademische Abschlüsse\n");
        prompt.append("- Für eine Pflege-Stelle: Pflegeausbildung, Empathie, medizinische Kenntnisse - NICHT IT-Zertifikate\n");
        prompt.append("- Für eine IT-Stelle: Programmiersprachen, Frameworks, IT-Projekte - relevante technische Ausbildung\n");
        prompt.append("- Für einfache Tätigkeiten: Motivation, Zuverlässigkeit, Teamfähigkeit, praktische Erfahrung\n\n");
        
        prompt.append("VERMEIDE:\n");
        prompt.append("- Überqualifikation zu zeigen (z.B. mehrere Hochschulabschlüsse für eine einfache Tätigkeit)\n");
        prompt.append("- Irrelevante Ausbildungen zu erwähnen, die den Arbeitgeber abschrecken könnten\n");
        prompt.append("- Zu viele Details, die nicht zur Stelle passen\n\n");
        
        // === JOB REQUIREMENTS (FIRST - so AI understands what's relevant) ===
        prompt.append("=== STELLENAUSSCHREIBUNG (BESTIMMT, WAS RELEVANT IST) ===\n");
        prompt.append("Position/Stellenbezeichnung: ").append(job.getPosition()).append("\n");
        prompt.append("Arbeitgeber/Unternehmen: ").append(job.getCompany()).append("\n");
        prompt.append("Standort/Arbeitsort: ").append(job.getLocation()).append("\n");
        
        if (job.getRequiredSkills() != null && !job.getRequiredSkills().isEmpty()) {
            prompt.append("Erforderliche Kenntnisse: ").append(String.join(", ", job.getRequiredSkills())).append("\n");
        }
        
        if (job.getPreferredSkills() != null && !job.getPreferredSkills().isEmpty()) {
            prompt.append("Wünschenswerte Kenntnisse: ").append(String.join(", ", job.getPreferredSkills())).append("\n");
        }
        
        if (job.getEducation() != null && !job.getEducation().isEmpty() && !job.getEducation().equals("Not specified")) {
            prompt.append("Anforderungen an die Ausbildung: ").append(job.getEducation()).append("\n");
        }
        
        if (job.getExperience() != null && !job.getExperience().isEmpty() && !job.getExperience().equals("Not specified")) {
            prompt.append("Berufserfahrung erforderlich: ").append(job.getExperience()).append("\n");
        }
        
        // === CANDIDATE BIOGRAPHY (for reference, but filter for relevance) ===
        prompt.append("\n=== VERFÜGBARE INFORMATIONEN ÜBER DEN BEWERBER (NUR RELEVANTES VERWENDEN!) ===\n");
        prompt.append("Name: ").append(biography.getName()).append("\n");
        
        // Education
        if (biography.getEducation() != null && !biography.getEducation().isEmpty()) {
            prompt.append("\nAusbildung des Bewerbers (NUR erwähnen, wenn relevant für die Stelle):\n");
            for (Biography.Education edu : biography.getEducation()) {
                prompt.append("- ").append(edu.getDegree());
                if (edu.getInstitution() != null && !edu.getInstitution().isEmpty()) {
                    prompt.append(" an ").append(edu.getInstitution());
                }
                if (edu.getStartDate() != null && edu.getEndDate() != null) {
                    prompt.append(" (").append(edu.getStartDate()).append(" - ").append(edu.getEndDate()).append(")");
                }
                prompt.append("\n");
            }
        }
        
        // Work Experience
        if (biography.getWorkExperience() != null && !biography.getWorkExperience().isEmpty()) {
            prompt.append("\nBerufserfahrung des Bewerbers (NUR relevante Erfahrungen erwähnen):\n");
            for (Biography.WorkExperience work : biography.getWorkExperience()) {
                prompt.append("- ").append(work.getPosition());
                if (work.getCompany() != null && !work.getCompany().isEmpty()) {
                    prompt.append(" bei ").append(work.getCompany());
                }
                if (work.getStartDate() != null && work.getEndDate() != null) {
                    prompt.append(" (").append(work.getStartDate()).append(" - ").append(work.getEndDate()).append(")");
                }
                prompt.append("\n");
                if (work.getResponsibilities() != null && !work.getResponsibilities().isEmpty()) {
                    prompt.append("  Aufgaben: ").append(work.getResponsibilities()).append("\n");
                }
            }
        }
        
        // Technical Skills
        if (biography.getTechnicalSkills() != null && !biography.getTechnicalSkills().isEmpty()) {
            prompt.append("\nFähigkeiten des Bewerbers (NUR relevante erwähnen): ");
            prompt.append(String.join(", ", biography.getTechnicalSkills())).append("\n");
        }
        
        // Languages
        if (biography.getLanguages() != null && !biography.getLanguages().isEmpty()) {
            prompt.append("\nSprachkenntnisse des Bewerbers:\n");
            for (Biography.Language lang : biography.getLanguages()) {
                prompt.append("- ").append(lang.getLanguage()).append(": ").append(lang.getLevel()).append("\n");
            }
        }
        
        // Certifications
        if (biography.getCertifications() != null && !biography.getCertifications().isEmpty()) {
            prompt.append("\nZertifikate des Bewerbers (NUR relevante erwähnen):\n");
            for (Biography.Certification cert : biography.getCertifications()) {
                prompt.append("- ").append(cert.getName());
                if (cert.getIssuer() != null && !cert.getIssuer().isEmpty()) {
                    prompt.append(" (").append(cert.getIssuer()).append(")");
                }
                prompt.append("\n");
            }
        }
        
        // === INSTRUCTIONS ===
        prompt.append("\n=== ANWEISUNGEN FÜR DAS ANSCHREIBEN ===\n");
        prompt.append("1. Der Betreff MUSS die exakte Stellenbezeichnung \"").append(job.getPosition()).append("\" enthalten\n");
        prompt.append("2. Der Firmenname \"").append(job.getCompany()).append("\" MUSS im Text genannt werden\n");
        prompt.append("3. Beginne mit \"Sehr geehrte Damen und Herren\"\n");
        prompt.append("4. In der Einleitung: Interesse an genau DIESER Stelle bei DIESEM Unternehmen zeigen\n");
        prompt.append("5. Im Hauptteil - WICHTIG:\n");
        prompt.append("   - Erwähne NUR Qualifikationen, die zur Stelle PASSEN\n");
        prompt.append("   - Bei einfachen Tätigkeiten: Betone Soft Skills wie Zuverlässigkeit, Motivation, Teamfähigkeit\n");
        prompt.append("   - Bei Fachpositionen: Erwähne relevante Ausbildung und Fachkenntnisse\n");
        prompt.append("   - Verknüpfe die RELEVANTEN Erfahrungen mit den Anforderungen der Stelle\n");
        prompt.append("   - KEINE akademischen Abschlüsse erwähnen, wenn sie für die Stelle nicht relevant oder sogar hinderlich wären\n");
        prompt.append("6. Schließe mit einer Bitte um ein Vorstellungsgespräch\n");
        prompt.append("7. Verwende \"Mit freundlichen Grüßen\" als Grußformel\n\n");
        
        prompt.append("KEINE Platzhalter wie [Name] oder [Unternehmen] verwenden!\n");
        prompt.append("Das Anschreiben soll authentisch wirken und zum Niveau der Stelle passen.");
        
        return prompt.toString();
    }
}
