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
        return generateAnschreiben(job, biography, null);
    }

    public String generateAnschreiben(JobRequirements job, Biography biography, String vacancyFullText) {
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
        
        String prompt = buildPrompt(job, biography, vacancyFullText);
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

    private String buildPrompt(JobRequirements job, Biography biography, String vacancyFullText) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("MISSION\n\n");
        prompt.append("You are a professional German job application consultant and prompt-engineer. ");
        prompt.append("Your task is to write a cover letter (Motivationsschreiben / Anschreiben). ");
        prompt.append("You must analyze a CV (biography) and a job description, and then ");
        prompt.append("create a high-quality German Motivationsschreiben / Anschreiben.\n\n");
        
        prompt.append("You must strictly follow the following 6-step methodology. ");
        prompt.append("Steps 1-5 are performed INTERNALLY and used ONLY for analysis. ");
        prompt.append("Your final output must contain ONLY the completed cover letter, WITHOUT any intermediate results.\n\n");
        
        prompt.append("CRITICAL: The final cover letter MUST be written in GERMAN language, following German business standards (DIN 5008). ");
        prompt.append("However, you will receive instructions in English for better understanding.\n\n");
        
        // === INPUTS ===
        prompt.append("INPUT\n\n");
        prompt.append("The user provides:\n");
        prompt.append("- A candidate biography / CV (free text)\n");
        prompt.append("- A job description / vacancy text\n\n");
        
        prompt.append("IMPORTANT: The following data is already fully provided. ");
        prompt.append("You MUST use this data to create the cover letter.\n\n");
        
        // Include full vacancy text if available
        if (vacancyFullText != null && !vacancyFullText.trim().isEmpty()) {
            prompt.append("=== FULL JOB POSTING TEXT ===\n");
            prompt.append(vacancyFullText).append("\n\n");
        }
        
        prompt.append("=== STRUCTURED JOB POSTING ===\n");
        appendJobPosting(prompt, job);
        prompt.append("\n");
        
        prompt.append("=== CANDIDATE BIOGRAPHY ===\n");
        appendBiography(prompt, biography);
        prompt.append("\n");
        
        prompt.append("END OF INPUT DATA\n");
        prompt.append("The above information is complete and must be used for creating the cover letter.\n\n");
        prompt.append("CRITICAL REMINDERS:\n");
        prompt.append("1. NAME: The candidate's name is in the section \"CANDIDATE BIOGRAPHY\" under \"Name:\" ");
        prompt.append("and MUST be used EXACTLY at the end of the letter after \"Mit freundlichen Grüßen\" - NO placeholders!\n");
        prompt.append("2. FACTS: Use ONLY information that is ACTUALLY in the biography. ");
        prompt.append("If it says \"unvollständige Ausbildung\" (incomplete education), do NOT write \"abgeschlossenen Ausbildung\" (completed education).\n");
        prompt.append("3. EXPERIENCE: Do NOT invent experiences. If there is no experience with agricultural machinery/vehicles mentioned, ");
        prompt.append("then do NOT write \"langjährige Erfahrung\" (extensive experience) - accept the gap.\n\n");
        
        // === STEP 1: EXTRACT FORMAL JOB REQUIREMENTS ===
        prompt.append("METHODOLOGY\n\n");
        prompt.append("STEP 1 — EXTRACT FORMAL JOB REQUIREMENTS\n\n");
        
        prompt.append("From the job posting text:\n");
        prompt.append("Identify and list ALL explicit requirements, including:\n");
        prompt.append("- Skills\n");
        prompt.append("- Qualifications\n");
        prompt.append("- Work experience\n");
        prompt.append("- Soft skills\n");
        prompt.append("- Industry knowledge\n");
        prompt.append("- Tools/Technologies\n");
        prompt.append("- Languages\n");
        prompt.append("- Education\n\n");
        
        prompt.append("Reformulate them as clear, neutral requirement statements.\n");
        prompt.append("Do not add interpretation yet.\n\n");
        
        prompt.append("Output format:\n");
        prompt.append("• Bullet list: \"Requirement: ...\"\n\n");
        
        // === STEP 2: INFER IMPLIED REQUIREMENTS ===
        prompt.append("STEP 2 — INFER IMPLIED REQUIREMENTS\n\n");
        
        prompt.append("Based on role type, industry, and responsibilities:\n");
        prompt.append("Derive plausible but unstated expectations, such as:\n");
        prompt.append("- Self-management / Self-responsibility\n");
        prompt.append("- Customer orientation\n");
        prompt.append("- Documentation skills\n");
        prompt.append("- Cross-functional communication\n");
        prompt.append("- Compliance / Regulatory conformity\n");
        prompt.append("- KPI orientation\n");
        prompt.append("- Project management skills\n");
        prompt.append("- Teamwork and cooperation skills\n");
        prompt.append("- Problem-solving skills\n");
        prompt.append("- Quality assurance\n\n");
        
        prompt.append("These must be reasonable for the German labor market and the given role.\n");
        prompt.append("Clearly mark them as inferred, not explicitly stated.\n\n");
        
        prompt.append("Output format:\n");
        prompt.append("• Bullet list: \"Implied requirement: ...\"\n\n");
        
        // === STEP 3: ANALYZE THE CV / BIOGRAPHY ===
        prompt.append("STEP 3 — ANALYZE THE CV / BIOGRAPHY\n\n");
        
        prompt.append("From the CANDIDATE BIOGRAPHY:\n");
        prompt.append("Extract skills, experience, achievements, education, tools, languages, certifications.\n");
        prompt.append("Do not summarize everything.\n");
        prompt.append("Focus on FACTS that could be matched to the job requirements.\n\n");
        
        prompt.append("IMPORTANT - USE EXACT FORMULATIONS:\n");
        prompt.append("- If the biography says \"unvollständige Ausbildung\" (incomplete education), use EXACTLY this formulation\n");
        prompt.append("- If the biography says \"Elektrotechniker\" (electrical technician), use this designation\n");
        prompt.append("- If NO experience with agricultural machinery/vehicles is mentioned, then this experience does NOT exist for this candidate\n");
        prompt.append("- Do NOT invent additional qualifications or experiences\n\n");
        
        prompt.append("Extract:\n");
        prompt.append("- Work experience (with relevance assessment to position and industry) - ONLY if mentioned in CV\n");
        prompt.append("- Education (with fit assessment to requirements) - use EXACT formulations like \"unvollständig\" (incomplete) or \"abgeschlossen\" (completed)\n");
        prompt.append("- Technical and transferable skills (only relevant and mentioned in CV)\n");
        prompt.append("- Achievements and measurable results (from relevant areas) - ONLY if mentioned in CV\n");
        prompt.append("- Certifications and further training - ONLY if mentioned in CV\n");
        prompt.append("- Languages and language skills - use exact levels from CV\n\n");
        
        prompt.append("Output format:\n");
        prompt.append("• Bullet list: \"Candidate data: ...\"\n\n");
        
        // === STEP 4: MATCH CV TO REQUIREMENTS ===
        prompt.append("STEP 4 — MATCH CV TO REQUIREMENTS\n\n");
        
        prompt.append("Create a structured mapping:\n");
        prompt.append("For each explicit and implied requirement, identify:\n");
        prompt.append("- Direct match (explicitly present in CV)\n");
        prompt.append("- Indirect match (transferable experience)\n");
        prompt.append("- Gap (not covered; must NOT be invented)\n\n");
        
        prompt.append("CRITICAL - PROHIBITION OF INVENTED FACTS:\n");
        prompt.append("- Use ONLY relevant experiences and education that are ACTUALLY in the biography\n");
        prompt.append("- If education is described as \"unvollständig\" (incomplete) or \"unvollständige Ausbildung\" (incomplete education), ");
        prompt.append("you must NOT write \"abgeschlossenen Ausbildung\" (completed education) - use the exact formulation from the biography\n");
        prompt.append("- If the CV does not mention experience in a specific area (e.g., agricultural machinery, vehicles), ");
        prompt.append("you must NOT write \"langjährige Erfahrung\" (extensive experience) or similar formulations\n");
        prompt.append("- Use ONLY what is EXPLICITLY in the biography - NO interpretations or additions\n");
        prompt.append("- If something is NOT in the CV, accept the gap - do NOT invent experiences or qualifications\n\n");
        
        prompt.append("CRITICAL: Use ONLY relevant experiences and education that match the position and industry AND are in the biography.\n\n");
        
        prompt.append("RELEVANCE CHECK:\n");
        prompt.append("Analyze each entry from work experience and education:\n");
        prompt.append("- Is this experience/education relevant for the advertised position?\n");
        prompt.append("- Does it fit the industry and job requirements?\n");
        prompt.append("- Could HR think the candidate is overqualified?\n\n");
        
        prompt.append("FILTER RULES - EXCLUDE if not relevant:\n");
        prompt.append("- Experiences from completely different industries/fields\n");
        prompt.append("- Higher qualifications that are significantly above requirements\n");
        prompt.append("- Specializations that do not fit the position\n\n");
        
        prompt.append("INCLUDE:\n");
        prompt.append("- Directly relevant work experience for the position or industry\n");
        prompt.append("- Transferable skills that fit the position\n");
        prompt.append("- Education that meets requirements or slightly exceeds them\n");
        prompt.append("- Industry-relevant experiences, even if position was slightly different\n\n");
        
        prompt.append("Output format:\n");
        prompt.append("Requirement → Match type → Supporting CV evidence (if any)\n\n");
        
        // === STEP 5: DEFINE COVER LETTER STRATEGY ===
        prompt.append("STEP 5 — DEFINE COVER LETTER STRATEGY\n\n");
        
        prompt.append("Derive the rhetorical and structural strategy for the cover letter:\n\n");
        
        prompt.append("What should be emphasized? (Core competencies, motivation, career logic)\n");
        prompt.append("• Emphasis:\n");
        prompt.append("  - Core competencies that directly match requirements\n");
        prompt.append("  - Motivation for the position and company\n");
        prompt.append("  - Logical career development and professional continuity\n");
        prompt.append("  - Measurable achievements and concrete results\n\n");
        
        prompt.append("What should be de-emphasized or avoided?\n");
        prompt.append("• De-emphasis:\n");
        prompt.append("  - Non-relevant experiences from other industries\n");
        prompt.append("  - Overqualification that could raise doubts about seriousness\n");
        prompt.append("  - Generic filler phrases without substance\n");
        prompt.append("  - Negative aspects or deficits\n\n");
        
        prompt.append("What positioning profile should be used?\n");
        prompt.append("• Positioning:\n");
        prompt.append("  - Experienced specialist (with corresponding experience)\n");
        prompt.append("  - Career changer (with industry change)\n");
        prompt.append("  - Technical generalist (with broad skillset)\n");
        prompt.append("  - Customer-oriented profile (with service/customer contact)\n");
        prompt.append("  - Solution-oriented problem solver (for analytical roles)\n");
        prompt.append("  - Team player and cooperation partner (for team roles)\n\n");
        
        prompt.append("What tone is appropriate?\n");
        prompt.append("• Tone & Style:\n");
        prompt.append("  - Formal, professional, German business style (Standard) - BUT write in German!\n");
        prompt.append("  - Confident, but not arrogant\n");
        prompt.append("  - Precise and concrete, not exaggerated\n");
        prompt.append("  - Adapted to industry and company size:\n");
        prompt.append("    * Public sector / NGO → more formal, mission-oriented tone\n");
        prompt.append("    * Startup / Tech → concise, results-oriented language\n");
        prompt.append("    * Healthcare / Education → empathetic, responsibility-oriented formulation\n");
        prompt.append("    * Finance → precise, compliance-oriented\n\n");
        
        // === STEP 6: CONSTRUCT THE FINAL GENERATION PROMPT ===
        prompt.append("STEP 6 — CONSTRUCT THE FINAL GENERATION PROMPT\n\n");
        
        prompt.append("Use all previous steps to generate the cover letter.\n\n");
        
        prompt.append("Specify:\n");
        prompt.append("- Language: GERMAN (formal business style, DIN 5008 standard) - THE FINAL LETTER MUST BE IN GERMAN!\n");
        prompt.append("- Length: 1 page / 3-4 paragraphs (approx. 250-400 words)\n");
        prompt.append("- Structure:\n");
        prompt.append("  1. Introduction paragraph: Reference to position, professional intent, high fit\n");
        prompt.append("  2. Qualification paragraph(s): Mapping job requirements → candidate qualifications with concrete examples\n");
        prompt.append("  3. Motivation & Cultural Fit: Why the company, not just the role; connection of personal values with organizational mission\n");
        prompt.append("  4. Closing paragraph: Readiness for interview, formal German closing etiquette\n\n");
        
        prompt.append("- Usage:\n");
        prompt.append("  * Job title: \"").append(job.getPosition()).append("\" MUST be in the subject line (Betreff)\n");
        prompt.append("  * Company name: \"").append(job.getCompany()).append("\" MUST be mentioned in the text\n");
        prompt.append("  * Salutation: \"Sehr geehrte Damen und Herren\" (Standard German greeting)\n");
        prompt.append("  * Closing: \"Mit freundlichen Grüßen\" followed by the candidate's name\n");
        prompt.append("  * CRITICAL - Candidate's name (MUST be used):\n");
        prompt.append("    - The name is in the CANDIDATE BIOGRAPHY section under \"Name:\"\n");
        prompt.append("    - You MUST use this name EXACTLY and COMPLETELY in the closing at the end\n");
        prompt.append("    - If the CV says \"Konstantin Glazunov\", use EXACTLY \"Konstantin Glazunov\", not just \"Konstantin\"\n");
        prompt.append("    - NO placeholders like [Name], [Kandidatenname], [Ihr Name] or similar - USE THE REAL NAME\n");
        prompt.append("    - The name MUST be on a new line after \"Mit freundlichen Grüßen\"\n");
        prompt.append("    - IF the name is NOT in the biography, then write \"Mit freundlichen Grüßen\" and leave a blank line\n");
        prompt.append("    - BUT: In 99% of cases the name is in the biography - use it!\n\n");
        
        prompt.append("- Prohibitions - ABSOLUTE PROHIBITION OF INVENTED INFORMATION:\n");
        prompt.append("  * NO invented facts - use ONLY what is in the CANDIDATE BIOGRAPHY\n");
        prompt.append("  * NO changing formulations: if it says \"unvollständige Ausbildung\" (incomplete education), you must NOT write \"abgeschlossenen Ausbildung\" (completed education)\n");
        prompt.append("  * NO invented experiences: if there is no experience with agricultural machinery in the CV, you must NOT write \"langjährige Erfahrung im Bereich Landmaschinen\" (extensive experience in agricultural machinery)\n");
        prompt.append("  * NO interpretations or additions - use EXACT formulations from the biography\n");
        prompt.append("  * ONLY use matched CV data - they MUST be explicitly in the biography\n");
        prompt.append("  * NO placeholders like [Name], [Kandidatenname], [Ihr Name], [Unternehmen] or similar\n");
        prompt.append("  * NO meta-commentary or process explanations\n");
        prompt.append("  * NO headings like \"Anschreiben\" or \"Motivationsschreiben\"\n");
        prompt.append("  * NO apologies or hints about missing data\n");
        prompt.append("  * NO omitting the name - the name MUST be present and from the biography\n\n");
        
        prompt.append("Instruction:\n");
        prompt.append("Integrate explicit + implied requirements with the candidate's relevant experiences.\n");
        prompt.append("BUT: Use ONLY experiences and qualifications that are ACTUALLY in the CANDIDATE BIOGRAPHY.\n");
        prompt.append("If something is NOT in the CV, accept it - do NOT invent and do NOT rephrase.\n");
        prompt.append("Use a professional German business tone.\n");
        prompt.append("The text must be ready to send.\n\n");
        
        // === OUTPUT FORMAT ===
        prompt.append("OUTPUT\n\n");
        
        prompt.append("CRITICAL - OUTPUT FORMAT:\n\n");
        prompt.append("You MUST perform all 6 steps (1-6) INTERNALLY and analyze, BUT:\n\n");
        prompt.append("Your FINAL OUTPUT must contain ONLY the completed cover letter!\n\n");
        
        prompt.append("FORBIDDEN in the output:\n");
        prompt.append("- NO Extracted Requirements (Step 1) in the output\n");
        prompt.append("- NO Implied Requirements (Step 2) in the output\n");
        prompt.append("- NO Relevant CV Data (Step 3) in the output\n");
        prompt.append("- NO Requirement-CV Mapping (Step 4) in the output\n");
        prompt.append("- NO Cover Letter Strategy (Step 5) in the output\n");
        prompt.append("- NO numbering or headings like \"1. Extracted Requirements\"\n");
        prompt.append("- NO bullet points with analysis results\n");
        prompt.append("- NO meta-commentary\n");
        prompt.append("- NO explanation of your process\n");
        prompt.append("- NO intermediate results or analyses\n\n");
        
        prompt.append("ALLOWED in the output:\n");
        prompt.append("- ONLY the final cover letter in GERMAN language\n");
        prompt.append("- The text starts directly with the subject line (Betreff) or salutation\n");
        prompt.append("- The text ends with THE REAL NAME from the biography after the closing\n");
        prompt.append("- The name MUST be taken from the section \"CANDIDATE BIOGRAPHY\" → \"Name:\"\n");
        prompt.append("- The text is completely ready to send\n\n");
        
        prompt.append("PROCESS:\n");
        prompt.append("1. Perform steps 1-5 INTERNALLY (do not show in output)\n");
        prompt.append("2. Use the results of steps 1-5 to create the cover letter\n");
        prompt.append("3. Output ONLY the completed cover letter (Step 6) - IN GERMAN!\n\n");
        
        prompt.append("EXAMPLE of correct output (with real name from biography):\n");
        prompt.append("Betreff: Bewerbung als ").append(job.getPosition() != null ? job.getPosition() : "[Job Title]").append("\n\n");
        prompt.append("Sehr geehrte Damen und Herren,\n\n");
        prompt.append("[Cover letter text here - 3-4 paragraphs, ONLY with facts from the biography, written in German]\n\n");
        prompt.append("Mit freundlichen Grüßen\n");
        if (biography != null && biography.getName() != null && !biography.getName().isEmpty()) {
            prompt.append(biography.getName()).append("\n\n");
            prompt.append("IMPORTANT: In the above example, the name \"").append(biography.getName()).append("\" from the biography was used. ");
            prompt.append("You MUST use the actual name from the \"CANDIDATE BIOGRAPHY\" section.\n\n");
        } else {
            prompt.append("[WRONG - do NOT do this!]\n");
            prompt.append("[Here the name from CANDIDATE BIOGRAPHY → Name: must stand - NO placeholders!]\n\n");
        }
        prompt.append("FORBIDDEN examples for names (do NOT do this!):\n");
        prompt.append("- [Ihr Name] ❌\n");
        prompt.append("- [Name] ❌\n");
        prompt.append("- [Kandidatenname] ❌\n");
        prompt.append("- Konstantin (if full name is Konstantin Glazunov) ❌\n\n");
        prompt.append("CORRECT: Use the EXACT full name from the biography (e.g., \"Konstantin Glazunov\") ✓\n\n");
        
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
