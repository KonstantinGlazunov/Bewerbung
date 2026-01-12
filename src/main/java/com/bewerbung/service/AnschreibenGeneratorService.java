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
        // Validate biography has data
        if (biography == null) {
            logger.error("Biography is null!");
            throw new IllegalArgumentException("Biography cannot be null");
        }
        
        logger.info("Generating Bewerbungsanschreiben for candidate: {} and position: {} at company: {}", 
                biography.getName(), job.getPosition(), job.getCompany());
        logger.info("Job location: {}, Required skills: {}", 
                job.getLocation(), 
                job.getRequiredSkills() != null ? String.join(", ", job.getRequiredSkills()) : "none");
        
        String prompt = buildPrompt(job, biography);
        logger.debug("Generated prompt for Anschreiben (length: {} chars)", prompt.length());
        
        // Log a summary of what data is available
        logger.info("Biography data summary - Name: {}, Education entries: {}, Work experience entries: {}, Skills: {}", 
                biography.getName() != null ? biography.getName() : "null",
                biography.getEducation() != null ? biography.getEducation().size() : 0,
                biography.getWorkExperience() != null ? biography.getWorkExperience().size() : 0,
                biography.getTechnicalSkills() != null ? biography.getTechnicalSkills().size() : 0);
        
        // Use HEAVY model for final document generation (higher quality)
        String anschreiben = openAiService.generateTextWithHeavyModel(prompt);
        
        logger.info("Successfully generated Bewerbungsanschreiben (length: {} characters)", 
                anschreiben.length());
        
        return anschreiben;
    }

    private String buildPrompt(JobRequirements job, Biography biography) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("META-PROMPT: GENERIERUNG VON BEWERBUNGSANSCHREIBEN\n\n");
        
        prompt.append("Rolle:\n");
        prompt.append("Du bist ein professioneller Karriereberater und deutscher Geschäftsschriftsteller. ");
        prompt.append("Deine Aufgabe ist es, ein hochwertiges Bewerbungsanschreiben auf Deutsch zu erstellen, ");
        prompt.append("basierend auf einer Kandidatenbiografie und einer Stellenausschreibung.\n\n");
        
        prompt.append("Du musst strikt der folgenden mehrstufigen analytischen Methodik folgen.\n\n");
        
        // === INPUTS ===
        prompt.append("📥 EINGABEN\n\n");
        
        prompt.append("WICHTIG: Die folgenden Daten sind bereits vollständig bereitgestellt. ");
        prompt.append("Du MUSST diese Daten verwenden, um das Anschreiben zu erstellen.\n\n");
        
        prompt.append("=== STELLENAUSSCHREIBUNG ===\n");
        appendJobPosting(prompt, job);
        prompt.append("\n");
        
        prompt.append("=== KANDIDATENBIOGRAFIE ===\n");
        appendBiography(prompt, biography);
        prompt.append("\n");
        
        prompt.append("ENDE DER EINGABEDATEN\n");
        prompt.append("Die obigen Informationen sind vollständig und müssen für die Erstellung des Anschreibens verwendet werden.\n\n");
        
        // === STEP 1: FORMAL DECONSTRUCTION ===
        prompt.append("🧠 SCHRITT 1 — FORMALE DEKONSTRUKTION\n\n");
        
        prompt.append("Extrahiere aus der Stellenausschreibung:\n");
        prompt.append("- Kernverantwortlichkeiten\n");
        prompt.append("- Erforderliche Fähigkeiten und Qualifikationen\n");
        prompt.append("- Soft Skills und Persönlichkeitsmerkmale\n");
        prompt.append("- Implizite Erwartungen (Ton, Arbeitsstil, Seniorität, Branchennormen)\n\n");
        
        prompt.append("Extrahiere aus der Biografie:\n");
        prompt.append("- Berufserfahrung\n");
        prompt.append("- Technische und übertragbare Fähigkeiten\n");
        prompt.append("- Erfolge und messbare Ergebnisse\n");
        prompt.append("- Motivationsfaktoren und Werte\n");
        prompt.append("- Sprachniveau, Kommunikationsstil, kultureller Kontext\n\n");
        
        prompt.append("WICHTIG: Verwende die oben bereitgestellten Daten aus der Biografie und Stellenausschreibung. ");
        prompt.append("Analysiere diese Daten und erstelle dann das Anschreiben.\n\n");
        
        // === STEP 2: SEMANTIC MATCHING ===
        prompt.append("🔎 SCHRITT 2 — SEMANTISCHE ZUORDNUNG\n\n");
        
        prompt.append("Für jede Schlüsselanforderung aus der Stellenausschreibung:\n");
        prompt.append("- Identifiziere die relevanteste passende Evidenz aus der Biografie.\n");
        prompt.append("- Priorisiere:\n");
        prompt.append("  • Direkte Erfahrung über indirekte\n");
        prompt.append("  • Messbare Ergebnisse über generische Aussagen\n");
        prompt.append("  • Branchenrelevante Terminologie über allgemeine Sprache\n\n");
        
        prompt.append("Wenn eine Anforderung nicht direkt erfüllt wird:\n");
        prompt.append("- Formuliere angrenzende Kompetenzen oder übertragbare Fähigkeiten um.\n");
        prompt.append("- Vermeide Erfindungen.\n\n");
        
        // === STEP 3: RHETORICAL STRUCTURE ===
        prompt.append("🧩 SCHRITT 3 — RHETORISCHE STRUKTUR\n\n");
        
        prompt.append("Konstruiere das Bewerbungsanschreiben mit folgender Logik:\n\n");
        
        prompt.append("Eröffnungsparagraph:\n");
        prompt.append("- Verweise explizit auf die Position.\n");
        prompt.append("- Zeige professionelle Absicht und hohe Passung auf.\n\n");
        
        prompt.append("Kompetenzparagraph(e):\n");
        prompt.append("- Ordne Stellenanforderungen → Kandidatenqualifikationen zu.\n");
        prompt.append("- Verwende konkrete Beispiele.\n");
        prompt.append("- Halte deutsche Geschäftsschreibkonventionen ein.\n\n");
        
        prompt.append("Motivation & Kulturelle Passung:\n");
        prompt.append("- Erkläre, warum das Unternehmen, nicht nur die Rolle.\n");
        prompt.append("- Verbinde persönliche Werte mit der Organisationsmission.\n\n");
        
        prompt.append("Schlussparagraph:\n");
        prompt.append("- Drücke Bereitschaft für ein Vorstellungsgespräch aus.\n");
        prompt.append("- Verwende formelle deutsche Schlussetikette.\n\n");
        
        // === STEP 4: STYLE & LANGUAGE CONSTRAINTS ===
        prompt.append("✍️ SCHRITT 4 — STIL & SPRACHBESCHRÄNKUNGEN\n\n");
        
        prompt.append("Sprache: Deutsch\n");
        prompt.append("Register: Formal, professionell, muttersprachlich\n");
        prompt.append("Ton: Selbstbewusst, präzise, nicht übertrieben\n");
        prompt.append("Länge: 1 Seite (≈ 250–400 Wörter)\n\n");
        
        prompt.append("Vermeide:\n");
        prompt.append("- Generische Füllphrasen\n");
        prompt.append("- Übermäßig emotionale oder werbliche Sprache\n");
        prompt.append("- Direktes Kopieren aus der Stellenausschreibung\n\n");
        
        prompt.append("Wenn die Biografie nicht-muttersprachliches Deutsch zeigt:\n");
        prompt.append("- Verwende klare, einfache aber korrekte Strukturen.\n");
        prompt.append("- Vermeide unnötige Idiome.\n\n");
        
        // === OUTPUT FORMAT ===
        prompt.append("📤 AUSGABEFORMAT\n\n");
        
        prompt.append("WICHTIG: Verwende die oben bereitgestellten Daten aus der Biografie und Stellenausschreibung. ");
        prompt.append("Die Daten sind vollständig und müssen verwendet werden.\n\n");
        
        prompt.append("Gib NUR das finale Bewerbungsanschreiben auf Deutsch zurück, mit:\n");
        prompt.append("- Keiner Meta-Kommentierung\n");
        prompt.append("- Keiner Erklärung deines Prozesses\n");
        prompt.append("- Keinen Überschriften wie \"Anschreiben\"\n");
        prompt.append("- Keinen Entschuldigungen oder Hinweisen auf fehlende Daten\n");
        prompt.append("- Der Text muss versandbereit sein und die bereitgestellten Informationen verwenden\n\n");
        
        prompt.append("WICHTIGE FORMALE ANFORDERUNGEN:\n");
        prompt.append("- Der Betreff MUSS die exakte Stellenbezeichnung \"").append(job.getPosition()).append("\" enthalten\n");
        prompt.append("- Der Firmenname \"").append(job.getCompany()).append("\" MUSS im Text genannt werden\n");
        prompt.append("- Beginne mit \"Sehr geehrte Damen und Herren\"\n");
        prompt.append("- Verwende \"Mit freundlichen Grüßen\" als Grußformel\n");
        prompt.append("- KEINE Platzhalter wie [Name] oder [Unternehmen] verwenden!\n\n");
        
        // === OPTIONAL ADVANCED MODE ===
        prompt.append("✅ OPTIONALER ERWEITERTER MODUS (falls zutreffend)\n\n");
        
        prompt.append("Wenn die Stellenausschreibung impliziert:\n");
        prompt.append("- Öffentlicher Sektor / NGO → formellerer, missionsorientierter Ton\n");
        prompt.append("- Startup / Tech → prägnante, ergebnisorientierte Sprache\n");
        prompt.append("- Gesundheitswesen / Bildung → empathische, verantwortungsorientierte Formulierung\n\n");
        
        prompt.append("Dann passe die rhetorische Betonung entsprechend an.\n");
        
        return prompt.toString();
    }
    
    private void appendJobPosting(StringBuilder prompt, JobRequirements job) {
        prompt.append("Position/Stellenbezeichnung: ").append(job.getPosition() != null ? job.getPosition() : "Nicht angegeben").append("\n");
        prompt.append("Arbeitgeber/Unternehmen: ").append(job.getCompany() != null ? job.getCompany() : "Nicht angegeben").append("\n");
        prompt.append("Standort/Arbeitsort: ").append(job.getLocation() != null ? job.getLocation() : "Nicht angegeben").append("\n");
        
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
        
        if (job.getLanguages() != null && !job.getLanguages().isEmpty()) {
            prompt.append("Erforderliche Sprachen: ").append(String.join(", ", job.getLanguages())).append("\n");
        }
    }
    
    private void appendBiography(StringBuilder prompt, Biography biography) {
        if (biography == null) {
            prompt.append("FEHLER: Biografie ist null. Bitte überprüfe die Eingabedaten.\n");
            return;
        }
        
        boolean hasData = false;
        
        if (biography.getName() != null && !biography.getName().isEmpty()) {
            prompt.append("Name: ").append(biography.getName()).append("\n");
            hasData = true;
        }
        
        if (biography.getEmail() != null && !biography.getEmail().isEmpty()) {
            prompt.append("E-Mail: ").append(biography.getEmail()).append("\n");
            hasData = true;
        }
        
        if (biography.getPhone() != null && !biography.getPhone().isEmpty()) {
            prompt.append("Telefon: ").append(biography.getPhone()).append("\n");
            hasData = true;
        }
        
        if (biography.getAddress() != null && !biography.getAddress().isEmpty()) {
            prompt.append("Adresse: ").append(biography.getAddress()).append("\n");
            hasData = true;
        }
        
        // Education
        if (biography.getEducation() != null && !biography.getEducation().isEmpty()) {
            prompt.append("\nAusbildung:\n");
            for (Biography.Education edu : biography.getEducation()) {
                prompt.append("- ").append(edu.getDegree() != null ? edu.getDegree() : "Nicht angegeben");
                if (edu.getInstitution() != null && !edu.getInstitution().isEmpty()) {
                    prompt.append(" an ").append(edu.getInstitution());
                }
                if (edu.getLocation() != null && !edu.getLocation().isEmpty()) {
                    prompt.append(", ").append(edu.getLocation());
                }
                if (edu.getStartDate() != null && edu.getEndDate() != null) {
                    prompt.append(" (").append(edu.getStartDate()).append(" - ").append(edu.getEndDate()).append(")");
                }
                if (edu.getDescription() != null && !edu.getDescription().isEmpty()) {
                    prompt.append("\n  Beschreibung: ").append(edu.getDescription());
                }
                prompt.append("\n");
            }
            hasData = true;
        }
        
        // Work Experience
        if (biography.getWorkExperience() != null && !biography.getWorkExperience().isEmpty()) {
            prompt.append("\nBerufserfahrung:\n");
            for (Biography.WorkExperience work : biography.getWorkExperience()) {
                prompt.append("- ").append(work.getPosition() != null ? work.getPosition() : "Nicht angegeben");
                if (work.getCompany() != null && !work.getCompany().isEmpty()) {
                    prompt.append(" bei ").append(work.getCompany());
                }
                if (work.getLocation() != null && !work.getLocation().isEmpty()) {
                    prompt.append(", ").append(work.getLocation());
                }
                if (work.getStartDate() != null && work.getEndDate() != null) {
                    prompt.append(" (").append(work.getStartDate()).append(" - ").append(work.getEndDate()).append(")");
                }
                prompt.append("\n");
                if (work.getResponsibilities() != null && !work.getResponsibilities().isEmpty()) {
                    prompt.append("  Aufgaben/Verantwortlichkeiten: ").append(work.getResponsibilities()).append("\n");
                }
            }
            hasData = true;
        }
        
        // Technical Skills
        if (biography.getTechnicalSkills() != null && !biography.getTechnicalSkills().isEmpty()) {
            prompt.append("\nTechnische Fähigkeiten: ").append(String.join(", ", biography.getTechnicalSkills())).append("\n");
            hasData = true;
        }
        
        // Soft Skills
        if (biography.getSoftSkills() != null && !biography.getSoftSkills().isEmpty()) {
            prompt.append("Soft Skills: ").append(String.join(", ", biography.getSoftSkills())).append("\n");
            hasData = true;
        }
        
        // Languages
        if (biography.getLanguages() != null && !biography.getLanguages().isEmpty()) {
            prompt.append("\nSprachkenntnisse:\n");
            for (Biography.Language lang : biography.getLanguages()) {
                prompt.append("- ").append(lang.getLanguage() != null ? lang.getLanguage() : "Nicht angegeben");
                if (lang.getLevel() != null && !lang.getLevel().isEmpty()) {
                    prompt.append(": ").append(lang.getLevel());
                }
                prompt.append("\n");
            }
            hasData = true;
        }
        
        // Certifications
        if (biography.getCertifications() != null && !biography.getCertifications().isEmpty()) {
            prompt.append("\nZertifikate:\n");
            for (Biography.Certification cert : biography.getCertifications()) {
                prompt.append("- ").append(cert.getName() != null ? cert.getName() : "Nicht angegeben");
                if (cert.getIssuer() != null && !cert.getIssuer().isEmpty()) {
                    prompt.append(" (").append(cert.getIssuer()).append(")");
                }
                if (cert.getDate() != null && !cert.getDate().isEmpty()) {
                    prompt.append(", Datum: ").append(cert.getDate());
                }
                prompt.append("\n");
            }
            hasData = true;
        }
        
        if (!hasData) {
            prompt.append("\nHINWEIS: Die Biografie enthält keine detaillierten Informationen. ");
            prompt.append("Erstelle trotzdem ein professionelles Anschreiben basierend auf den verfügbaren Grundinformationen.\n");
        }
    }
}
