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
        return generateAnschreiben(job, biography, null, null, null);
    }

    public String generateAnschreiben(JobRequirements job, Biography biography, String vacancyFullText) {
        return generateAnschreiben(job, biography, vacancyFullText, null, null);
    }

    public String generateAnschreiben(JobRequirements job, Biography biography, String vacancyFullText, String wishes) {
        return generateAnschreiben(job, biography, vacancyFullText, wishes, null);
    }

    public String generateAnschreiben(JobRequirements job, Biography biography, String vacancyFullText, String wishes, String language) {
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
        
        // Log user wishes if provided
        if (wishes != null && !wishes.trim().isEmpty()) {
            logger.info("User wishes/corrections provided (length: {} chars): {}", wishes.length(), wishes);
        } else {
            logger.info("No user wishes/corrections provided");
        }
        
        // Default to German if language not specified
        if (language == null || language.trim().isEmpty()) {
            language = "de";
        }
        language = language.trim().toLowerCase(); // Normalize language
        logger.info("Generating cover letter in language: {} (normalized)", language);
        
        String prompt = buildPrompt(job, biography, vacancyFullText, wishes, language);
        logger.debug("Generated prompt for Anschreiben (length: {} chars, language: {})", prompt.length(), language);
        
        // Log a snippet of the prompt to verify language setting
        if (prompt.contains("CRITICAL: The final cover letter MUST be written in")) {
            int startIdx = prompt.indexOf("CRITICAL: The final cover letter MUST be written in");
            String snippet = prompt.substring(startIdx, Math.min(startIdx + 150, prompt.length()));
            logger.info("Prompt language instruction snippet: {}", snippet);
        }
        
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

    private String buildPrompt(JobRequirements job, Biography biography, String vacancyFullText, String wishes, String language) {
        StringBuilder prompt = new StringBuilder();
        
        // Default to German if invalid language
        if (language == null || (!language.equals("de") && !language.equals("ru") && !language.equals("en"))) {
            logger.warn("Invalid language '{}' provided, defaulting to 'de'", language);
            language = "de";
        }
        logger.info("Building prompt with language: {}", language);
        
        prompt.append("MISSION\n\n");
        
        // CRITICAL: Language specification at the very beginning
        if ("ru".equals(language)) {
            prompt.append("⚠️⚠️⚠️ CRITICAL LANGUAGE REQUIREMENT: YOU MUST WRITE THE ENTIRE COVER LETTER IN RUSSIAN LANGUAGE! ⚠️⚠️⚠️\n");
            prompt.append("The output language is RUSSIAN. Every word, every sentence, every paragraph MUST be in RUSSIAN.\n");
            prompt.append("Do NOT write in German. Do NOT write in English. Write ONLY in RUSSIAN.\n\n");
        } else if ("en".equals(language)) {
            prompt.append("⚠️⚠️⚠️ CRITICAL LANGUAGE REQUIREMENT: YOU MUST WRITE THE ENTIRE COVER LETTER IN BRITISH ENGLISH! ⚠️⚠️⚠️\n");
            prompt.append("The output language is BRITISH ENGLISH. Every word, every sentence, every paragraph MUST be in BRITISH ENGLISH.\n");
            prompt.append("Do NOT write in German. Do NOT write in American English. Write ONLY in BRITISH ENGLISH.\n\n");
        } else {
            prompt.append("⚠️⚠️⚠️ CRITICAL LANGUAGE REQUIREMENT: YOU MUST WRITE THE ENTIRE COVER LETTER IN GERMAN LANGUAGE! ⚠️⚠️⚠️\n");
            prompt.append("The output language is GERMAN. Every word, every sentence, every paragraph MUST be in GERMAN.\n\n");
        }
        
        // Language-specific mission statement
        if ("de".equals(language)) {
            prompt.append("You are a professional German job application consultant and prompt-engineer. ");
            prompt.append("Your task is to write a cover letter (Motivationsschreiben / Anschreiben). ");
            prompt.append("You must analyze a CV (biography) and a job description, and then ");
            prompt.append("create a high-quality German Motivationsschreiben / Anschreiben.\n\n");
        } else if ("ru".equals(language)) {
            prompt.append("You are a professional Russian job application consultant and prompt-engineer. ");
            prompt.append("Your task is to write a cover letter (мотивационное письмо / сопроводительное письмо). ");
            prompt.append("You must analyze a CV (biography) and a job description, and then ");
            prompt.append("create a high-quality Russian motivational letter following Russian standards.\n\n");
        } else { // "en"
            prompt.append("You are a professional British job application consultant and prompt-engineer. ");
            prompt.append("Your task is to write a cover letter. ");
            prompt.append("You must analyze a CV (biography) and a job description, and then ");
            prompt.append("create a high-quality British-style cover letter following UK standards.\n\n");
        }
        
        prompt.append("You must strictly follow the following 6-step methodology. ");
        prompt.append("Steps 1-5 are performed INTERNALLY and used ONLY for analysis. ");
        prompt.append("Your final output must contain ONLY the completed cover letter, WITHOUT any intermediate results.\n\n");
        
        // Language and country-specific requirements
        if ("de".equals(language)) {
            prompt.append("CRITICAL: The final cover letter MUST be written in GERMAN language, following German business standards (DIN 5008). ");
            prompt.append("This includes:\n");
            prompt.append("- Formal style and professional tone\n");
            prompt.append("- Structure with Betreff (subject line)\n");
            prompt.append("- Proper formatting according to DIN 5008\n");
            prompt.append("- Formal greeting and closing\n");
            prompt.append("- Professional, business-like language throughout\n");
            prompt.append("However, you will receive instructions in English for better understanding.\n\n");
        } else if ("ru".equals(language)) {
            prompt.append("CRITICAL: The final cover letter MUST be written in RUSSIAN language, following Russian business standards. ");
            prompt.append("This includes:\n");
            prompt.append("- Warmer, more personal tone while maintaining professionalism\n");
            prompt.append("- Strong emphasis on motivation and personal interest\n");
            prompt.append("- Russian business letter formatting standards\n");
            prompt.append("- Appropriate greeting and closing for Russian business correspondence\n");
            prompt.append("- Balance between professionalism and personal touch\n");
            prompt.append("However, you will receive instructions in English for better understanding.\n\n");
        } else { // "en"
            prompt.append("CRITICAL: The final cover letter MUST be written in BRITISH ENGLISH, following UK business standards. ");
            prompt.append("This includes:\n");
            prompt.append("- Balance between professionalism and personality\n");
            prompt.append("- British English spelling and conventions (e.g., 'organise' not 'organize')\n");
            prompt.append("- UK business letter formatting standards\n");
            prompt.append("- Professional yet approachable tone\n");
            prompt.append("- Appropriate greeting and closing for UK business correspondence\n");
            prompt.append("However, you will receive instructions in English for better understanding.\n\n");
        }
        
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
        
        // Add priority rule for wishes vs biography
        if (wishes != null && !wishes.trim().isEmpty()) {
            prompt.append("CONFLICT RESOLUTION RULE:\n");
            prompt.append("USER INPUT IS DIVIDED INTO THREE DISTINCT CATEGORIES:\n\n");
            prompt.append("1. FACTUAL CORRECTIONS\n");
            prompt.append("2. FACT EXCLUSIONS\n");
            prompt.append("3. STYLE & PRESENTATION INSTRUCTIONS\n\n");
            prompt.append("These categories have different purposes and MUST NOT be mixed.\n\n");
            prompt.append("User wishes may override biography facts ONLY in the form of:\n");
            prompt.append("- explicit factual corrections\n");
            prompt.append("- explicit fact exclusions\n\n");
            prompt.append("User wishes may NEVER be used to invent new facts, roles, experience, skills, or timelines.\n\n");
            prompt.append("USER WISHES are a source of factual information, alongside the CANDIDATE BIOGRAPHY.\n");
            prompt.append("Use ONLY information from the biography and explicit factual statements in USER WISHES.\n");
            prompt.append("Do NOT infer, assume, or extend beyond these sources.\n\n");
        }
        
        prompt.append("CRITICAL REMINDERS:\n");
        prompt.append("1. NAME: The candidate's name is in the section \"CANDIDATE BIOGRAPHY\" under \"Name:\" ");
        if ("de".equals(language)) {
            prompt.append("and MUST be used EXACTLY at the end of the letter after \"Mit freundlichen Grüßen\" - NO placeholders!\n");
        } else if ("ru".equals(language)) {
            prompt.append("and MUST be used EXACTLY at the end of the letter after appropriate Russian closing (e.g., \"С уважением\") - NO placeholders!\n");
        } else { // "en"
            prompt.append("and MUST be used EXACTLY at the end of the letter after appropriate British closing (e.g., \"Yours sincerely\") - NO placeholders!\n");
        }
        prompt.append("2. FACTS: Use ONLY information from the biography ");
        if (wishes != null && !wishes.trim().isEmpty()) {
            prompt.append("and explicit factual statements in USER WISHES.\n");
            prompt.append("   If a wish contradicts the biography, treat it as a factual correction and follow the wish.\n");
        } else {
            prompt.append("(if no user wishes are provided).\n");
        }
        if ("de".equals(language)) {
            prompt.append("   If it says \"unvollständige Ausbildung\" (incomplete education), do NOT write \"abgeschlossenen Ausbildung\" (completed education).\n");
        } else {
            prompt.append("   Use EXACT formulations from the biography - do NOT change or improve them.\n");
        }
        prompt.append("   Do NOT infer, assume, or extend beyond these sources.\n");
        prompt.append("   TEMPORAL RULE:\n");
        prompt.append("   Never assume current employment.\n");
        prompt.append("   If the biography does not explicitly state \"present\", \"current\", or equivalent,\n");
        prompt.append("   assume the role has ended.\n");
        prompt.append("3. EXPERIENCE: Do NOT invent experiences. Use ONLY what is explicitly mentioned in the biography");
        if (wishes != null && !wishes.trim().isEmpty()) {
            prompt.append(" or explicitly stated in USER WISHES");
        }
        prompt.append(".\n\n");
        
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
        
        prompt.append("IMPORTANT - PRESERVE MEANING, ALLOW REFORMULATION:\n");
        prompt.append("- Do NOT change the MEANING of formulations from the biography\n");
        prompt.append("- Reformulation for coherent and natural text is ALLOWED, provided factual accuracy is preserved\n");
        prompt.append("- If the biography says \"unvollständige Ausbildung\" (incomplete education), preserve this meaning (do NOT write \"completed education\")\n");
        prompt.append("- If the biography says \"Elektrotechniker\" (electrical technician), preserve this designation\n");
        prompt.append("- If NO experience with agricultural machinery/vehicles is mentioned, then this experience does NOT exist for this candidate\n");
        prompt.append("- Do NOT invent additional qualifications or experiences\n\n");
        prompt.append("ALLOWED:\n");
        prompt.append("- Reformulating CV bullet points into coherent sentences\n");
        prompt.append("- Combining several CV points into one logical statement\n\n");
        prompt.append("FORBIDDEN:\n");
        prompt.append("- Enhancing or embellishing experience\n");
        prompt.append("- Adding quantitative or qualitative assessments not present in the CV\n\n");
        
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
        
        prompt.append("⚠️⚠️⚠️ CRITICAL - PROHIBITION OF INVENTED FACTS ⚠️⚠️⚠️\n");
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
        prompt.append("  - Measurable achievements and concrete results — ONLY if explicitly present in the CV or Wishes\n\n");
        
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
        if ("de".equals(language)) {
            prompt.append("  - Formal, professional, German business style (Standard) - BUT write in German!\n");
            prompt.append("  - Confident, but not arrogant\n");
            prompt.append("  - Precise and concrete, not exaggerated\n");
            prompt.append("  - Adapted to industry and company size:\n");
            prompt.append("    * Public sector / NGO → more formal, mission-oriented tone\n");
            prompt.append("    * Startup / Tech → concise, results-oriented language\n");
            prompt.append("    * Healthcare / Education → empathetic, responsibility-oriented formulation\n");
            prompt.append("    * Finance → precise, compliance-oriented\n\n");
        } else if ("ru".equals(language)) {
            prompt.append("  - Warmer, more personal tone while maintaining professionalism\n");
            prompt.append("  - Strong emphasis on motivation and personal interest\n");
            prompt.append("  - Balance between professionalism and personal touch\n");
            prompt.append("  - Confident, but approachable\n");
            prompt.append("  - Adapted to industry and company size:\n");
            prompt.append("    * Public sector / NGO → respectful, mission-oriented tone\n");
            prompt.append("    * Startup / Tech → dynamic, results-oriented language\n");
            prompt.append("    * Healthcare / Education → empathetic, caring formulation\n");
            prompt.append("    * Finance → precise, trustworthy\n\n");
        } else { // "en"
            prompt.append("  - Balance between professionalism and personality\n");
            prompt.append("  - Professional yet approachable tone\n");
            prompt.append("  - Confident, but not arrogant\n");
            prompt.append("  - Precise and concrete, not exaggerated\n");
            prompt.append("  - Adapted to industry and company size:\n");
            prompt.append("    * Public sector / NGO → respectful, mission-oriented tone\n");
            prompt.append("    * Startup / Tech → concise, results-oriented language\n");
            prompt.append("    * Healthcare / Education → empathetic, responsibility-oriented formulation\n");
            prompt.append("    * Finance → precise, compliance-oriented\n\n");
        }
        
        // === STEP 6: GENERATE THE FINAL COVER LETTER ===
        prompt.append("STEP 6 — GENERATE THE FINAL COVER LETTER\n\n");
        
        // Add user wishes/corrections at the beginning of STEP 6
        if (wishes != null && !wishes.trim().isEmpty()) {
            prompt.append("CRITICAL: USER WISHES AND CORRECTIONS\n\n");
            prompt.append("USER INPUT IS DIVIDED INTO THREE DISTINCT CATEGORIES:\n\n");
            prompt.append("1. FACTUAL CORRECTIONS - corrections of facts from biography\n");
            prompt.append("2. FACT EXCLUSIONS - exclusions/denials of specific information\n");
            prompt.append("3. STYLE & PRESENTATION INSTRUCTIONS - style, tone, structure changes\n\n");
            prompt.append("These categories have different purposes and MUST NOT be mixed.\n\n");
            
            // Categorize wishes
            // Split by lines, but also handle explicit markers that might span multiple lines
            String[] wishLines = wishes.split("\n");
            java.util.List<WishCategory> categorizedWishes = new java.util.ArrayList<>();
            
            // Process each line or group of lines
            StringBuilder currentWish = new StringBuilder();
            for (String line : wishLines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    // Empty line - process accumulated wish if any
                    if (currentWish.length() > 0) {
                        categorizedWishes.add(categorizeWish(currentWish.toString()));
                        currentWish.setLength(0);
                    }
                } else {
                    // Check if line starts with explicit marker
                    String lower = trimmed.toLowerCase();
                    if (lower.startsWith("factual_correction:") || lower.startsWith("factual correction:")) {
                        // Process previous wish if any
                        if (currentWish.length() > 0) {
                            categorizedWishes.add(categorizeWish(currentWish.toString()));
                            currentWish.setLength(0);
                        }
                        // Extract text after marker
                        String text = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                        if (!text.isEmpty()) {
                            categorizedWishes.add(new WishCategory("FACTUAL_CORRECTION", text));
                        }
                    } else if (lower.startsWith("fact_exclusion:") || lower.startsWith("fact exclusion:")) {
                        // Process previous wish if any
                        if (currentWish.length() > 0) {
                            categorizedWishes.add(categorizeWish(currentWish.toString()));
                            currentWish.setLength(0);
                        }
                        // Extract text after marker
                        String text = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                        if (!text.isEmpty()) {
                            categorizedWishes.add(new WishCategory("FACT_EXCLUSION", text));
                        }
                    } else if (lower.startsWith("style_instruction:") || lower.startsWith("style instruction:")) {
                        // Process previous wish if any
                        if (currentWish.length() > 0) {
                            categorizedWishes.add(categorizeWish(currentWish.toString()));
                            currentWish.setLength(0);
                        }
                        // Extract text after marker
                        String text = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                        if (!text.isEmpty()) {
                            categorizedWishes.add(new WishCategory("STYLE_INSTRUCTION", text));
                        }
                    } else {
                        // Accumulate line for later categorization
                        if (currentWish.length() > 0) {
                            currentWish.append(" ");
                        }
                        currentWish.append(trimmed);
                    }
                }
            }
            // Process remaining accumulated wish
            if (currentWish.length() > 0) {
                categorizedWishes.add(categorizeWish(currentWish.toString()));
            }
            
            // If no wishes were categorized, treat entire input as single wish
            if (categorizedWishes.isEmpty()) {
                categorizedWishes.add(categorizeWish(wishes.trim()));
            }
            
            // Display categorized wishes in prompt
            prompt.append("FORMALIZED CATEGORIZATION OF USER WISHES:\n\n");
            
            java.util.List<String> factualCorrections = new java.util.ArrayList<>();
            java.util.List<String> factExclusions = new java.util.ArrayList<>();
            java.util.List<String> styleInstructions = new java.util.ArrayList<>();
            
            for (WishCategory cat : categorizedWishes) {
                if ("FACTUAL_CORRECTION".equals(cat.category)) {
                    factualCorrections.add(cat.text);
                } else if ("FACT_EXCLUSION".equals(cat.category)) {
                    factExclusions.add(cat.text);
                } else {
                    styleInstructions.add(cat.text);
                }
            }
            
            if (!factualCorrections.isEmpty()) {
                prompt.append("FACTUAL_CORRECTION:\n");
                for (String correction : factualCorrections) {
                    prompt.append("  - ").append(correction).append("\n");
                }
                prompt.append("\n");
            }
            
            if (!factExclusions.isEmpty()) {
                prompt.append("FACT_EXCLUSION:\n");
                for (String exclusion : factExclusions) {
                    prompt.append("  - ").append(exclusion).append("\n");
                }
                prompt.append("\n");
            }
            
            if (!styleInstructions.isEmpty()) {
                prompt.append("STYLE_INSTRUCTION:\n");
                for (String instruction : styleInstructions) {
                    prompt.append("  - ").append(instruction).append("\n");
                }
                prompt.append("\n");
            }
            
            prompt.append("FORMALIZED CATEGORIZATION RULES:\n");
            prompt.append("User wishes are automatically categorized using the following rules:\n\n");
            prompt.append("1. EXPLICIT MARKERS (highest priority):\n");
            prompt.append("   If wish starts with FACTUAL_CORRECTION:, FACT_EXCLUSION:, or STYLE_INSTRUCTION: → use that category\n");
            prompt.append("   Example: \"FACT_EXCLUSION: не упоминай опыт работы разработчиком\" → FACT_EXCLUSION\n\n");
            prompt.append("2. AUTOMATIC PATTERN DETECTION:\n");
            prompt.append("   If wish starts with \"я не\", \"я никогда\", \"не используй\", \"don't\", \"never\", \"не упоминай\", \"keine\", \"nicht\" → FACT_EXCLUSION\n");
            prompt.append("   If wish starts with \"вместо\", \"instead\", \"correct\", \"исправь\", \"замени\", \"statt\" → FACTUAL_CORRECTION\n");
            prompt.append("   All other wishes → STYLE_INSTRUCTION\n\n");
            prompt.append("IMPORTANT: Categories are applied automatically. The categorization above shows how each wish was classified.\n\n");
            prompt.append("IMPORTANT: User wishes may be written in different languages (German, Russian, English, etc.). ");
            prompt.append("You MUST understand and interpret them correctly regardless of language.\n\n");
            
            // Check for negative statements (no experience, never worked, etc.)
            String wishesLower = wishes.toLowerCase();
            boolean hasNegativeStatement = wishesLower.contains("нет опыта") || wishesLower.contains("no experience") || 
                                         wishesLower.contains("keine erfahrung") || wishesLower.contains("никогда не работал") ||
                                         wishesLower.contains("never worked") || wishesLower.contains("nie gearbeitet") ||
                                         wishesLower.contains("не работал") || wishesLower.contains("didn't work") ||
                                         wishesLower.contains("не имею") || wishesLower.contains("don't have");
            
            if (hasNegativeStatement) {
                prompt.append("🚨🚨🚨 CRITICAL NEGATIVE STATEMENT DETECTED 🚨🚨🚨\n\n");
                prompt.append("The user has explicitly stated that they DO NOT have experience, have NEVER worked, or DO NOT have certain qualifications.\n");
                prompt.append("IMPORTANT: Negative statements apply ONLY to the explicitly denied role, field, or qualification.\n");
                prompt.append("You MUST NOT mention or imply any experience, work history, or qualifications ");
                prompt.append("that the user explicitly denies, EVEN IF they appear in the CANDIDATE BIOGRAPHY.\n\n");
                prompt.append("INTERPRETATION RULES FOR NEGATIVE STATEMENTS:\n");
                prompt.append("General statements like \"I have no experience\" or \"У меня нет опыта\" must be interpreted in context:\n");
                prompt.append("1. If the denial is role-specific (e.g., \"I never worked as developer\" or \"Я никогда не работал разработчиком\") → ");
                prompt.append("exclude ONLY that specific role/field, but other experience from biography can still be used\n");
                prompt.append("2. If the denial is absolute and explicit (e.g., \"I have NO professional experience at all\" or \"У меня вообще нет опыта работы\") → ");
                prompt.append("exclude all professional experience, but education, training, and transferable skills can still be used\n");
                prompt.append("3. If ambiguous or unclear → prefer conservative interpretation:\n");
                prompt.append("   - Assume the denial is role/field-specific unless explicitly stated as absolute\n");
                prompt.append("   - Do NOT exclude unrelated experience from biography\n");
                prompt.append("   - Avoid inventing, but do NOT erase valid experience that is not explicitly denied\n\n");
                prompt.append("Clarification:\n");
                prompt.append("Statements about CURRENT STATUS (e.g., \"I am currently unemployed\" or \"Я сейчас безработный\") ");
                prompt.append("do NOT negate past experience unless explicitly stated.\n\n");
                prompt.append("Example (semantic illustration - shows the logic, not the language):\n");
                prompt.append("\"I am currently unemployed\" ≠ \"I never worked before\"\n");
                prompt.append("\"Я сейчас безработный\" ≠ \"Я никогда не работал\"\n");
                prompt.append("(The final output MUST still be in the target language)\n\n");
                prompt.append("STRICT RULES:\n");
                prompt.append("- If user says \"I never worked as X\" or \"Я никогда не работал X\" → DO NOT mention work experience as X, even if biography shows it\n");
                prompt.append("- If user explicitly denies something specific from biography → That specific information is FORBIDDEN to use\n");
                prompt.append("- If user denies experience in a field → Exclude only that field, not all experience\n");
                prompt.append("- Focus on motivation, education, transferable skills, or other aspects that are NOT denied\n");
                prompt.append("- DO NOT invent or imply experience that user explicitly says they don't have\n\n");
                prompt.append("EXAMPLES:\n");
                prompt.append("NOTE: The following examples are semantic illustrations only. ");
                prompt.append("The final output MUST still be in the target language (");
                if ("ru".equals(language)) {
                    prompt.append("RUSSIAN");
                } else if ("en".equals(language)) {
                    prompt.append("BRITISH ENGLISH");
                } else {
                    prompt.append("GERMAN");
                }
                prompt.append(").\n\n");
                prompt.append("1. If user says \"Я никогда не работал разработчиком\" (I never worked as developer):\n");
                prompt.append("   ❌ FORBIDDEN: Any mention of software development work experience\n");
                prompt.append("   ❌ FORBIDDEN: Any mention of developer positions, even if in biography\n");
                prompt.append("   ✓ ALLOWED: Other work experience from biography (e.g., if user worked as tester, manager, etc.)\n");
                prompt.append("   ✓ ALLOWED: [Semantic example: acknowledge lack of experience in the denied field]\n");
                prompt.append("   ✓ ALLOWED: Education, training, projects, motivation, transferable skills\n\n");
                prompt.append("2. If user says \"У меня нет опыта работы\" (I have no work experience) - ambiguous:\n");
                prompt.append("   → Interpret conservatively: assume user means no RELEVANT experience for the position\n");
                prompt.append("   → If biography shows unrelated work (e.g., retail, unrelated field), it can be mentioned briefly\n");
                prompt.append("   → Focus on education, training, motivation, willingness to learn\n");
                prompt.append("   → Do NOT completely erase biography if denial is not explicitly absolute\n\n");
                prompt.append("3. If user says \"У меня вообще нет опыта работы, никогда не работал\" (I have absolutely no work experience, never worked):\n");
                prompt.append("   → This is explicit and absolute → exclude all professional work experience\n");
                prompt.append("   ✓ ALLOWED: Education, training, courses, projects, motivation\n");
                prompt.append("   ✓ ALLOWED: Transferable skills from education or personal projects\n\n");
            }
            
            prompt.append("CONFLICT RESOLUTION RULE:\n");
            prompt.append("User wishes may override biography facts ONLY in the form of:\n");
            prompt.append("- explicit factual corrections\n");
            prompt.append("- explicit fact exclusions\n\n");
            prompt.append("User wishes may NEVER be used to invent new facts, roles, experience, skills, or timelines.\n\n");
            prompt.append("If user wishes CONTRADICT or CONFLICT with information from CANDIDATE BIOGRAPHY, ");
            prompt.append("apply the conflict resolution rule above. Use what the user requested, not what is in the biography.\n");
            prompt.append("The biography is a reference, but user wishes are DIRECT INSTRUCTIONS that must be followed.\n");
            prompt.append("If user explicitly denies experience/qualifications, apply the denial ONLY to the explicitly denied role/field/qualification.\n");
            prompt.append("Do NOT erase unrelated information from biography unless the denial is explicitly absolute.\n\n");
            prompt.append("If the user requests changes to salutation, tone, structure, or content, you MUST follow these requests exactly.\n");
            prompt.append("Examples of user wishes (semantic illustrations - apply the logic, not the language):\n");
            prompt.append("- \"Вместо [formal greeting], [informal greeting]\" → Use the informal greeting instead of formal\n");
            prompt.append("- \"Instead of [formal greeting], use [informal greeting]\" → Use the informal greeting instead\n");
            prompt.append("- \"Statt [formal greeting], [informal greeting]\" → Use the informal greeting instead\n");
            prompt.append("- If user says \"Don't mention X from biography\" → Do NOT mention X, even if it's in biography\n");
            prompt.append("- If user says \"Emphasize Y instead of Z\" → Emphasize Y, even if Z is more prominent in biography\n");
            prompt.append("- Any other request about salutation, tone, structure, or content → Follow it exactly\n\n");
            
            prompt.append("IMPORTANT LIMITATION:\n");
            prompt.append("User wishes may override facts ONLY by explicit factual correction or explicit exclusion.\n");
            prompt.append("User wishes must NEVER be used to invent new facts, experience, roles, skills, or timelines.\n\n");
        }
        
        // === HUMANIZATION CHECK ===
        prompt.append("CRITICAL: HUMANIZATION AND NATURAL WRITING STYLE\n\n");
        
        prompt.append("You are a HUMAN, not an AI.\n");
        prompt.append("You are writing a cover letter for a REAL recruiter.\n\n");
        
        prompt.append("GOAL:\n");
        prompt.append("Write the text so that it looks like it was written by a LIVING PERSON,\n");
        prompt.append("without the feeling of a template, neural network, or \"corporate\" style.\n\n");
        
        prompt.append("CONTEXT:\n");
        if ("de".equals(language)) {
            prompt.append("- Country: Germany\n");
        } else if ("ru".equals(language)) {
            prompt.append("- Country: Russia\n");
        } else { // "en"
            prompt.append("- Country: United Kingdom\n");
        }
        prompt.append("- Letter type: Cover letter / Motivational letter for job application\n");
        prompt.append("- Tone: Calm, confident, slightly personal, without pathos\n\n");
        
        prompt.append("STRICT LIMITATIONS:\n");
        prompt.append("- Do NOT use clichés (e.g., \"motivated\", \"dynamic team\", \"quick learner\", ");
        if ("de".equals(language)) {
            prompt.append("\"es sei darauf hingewiesen\", \"auf diese Weise\", \"zusammenfassend\", etc.)\n");
        } else if ("ru".equals(language)) {
            prompt.append("\"следует отметить\", \"таким образом\", \"в заключение\", etc.)\n");
        } else { // "en"
            prompt.append("\"it should be noted\", \"thus\", \"in conclusion\", etc.)\n");
        }
        prompt.append("- Maintain standard letter structure (especially for DE / DIN 5008),\n");
        prompt.append("  but vary the internal structure of paragraphs:\n");
        prompt.append("  * Different order of arguments\n");
        prompt.append("  * Different sentence lengths\n");
        prompt.append("  * Different rhythm of presentation\n");
        prompt.append("- Vary sentence length (short and long sentences)\n");
        prompt.append("- Minimum adjectives, more actions and facts\n");
        prompt.append("- The text should sound like it was written in one evening:\n");
        prompt.append("  * Without excessively polished formulations\n");
        prompt.append("  * Without academic complexity\n");
        prompt.append("  * Without repeating structural patterns\n\n");
        
        prompt.append("SIGNS OF \"HUMAN\" TEXT:\n");
        prompt.append("- There is logic of reasoning, not enumeration of qualities\n");
        prompt.append("- There are 1-2 careful subjective formulations\n");
        prompt.append("- Light stylistic roughness is allowed:\n");
        prompt.append("  * Asymmetric sentences\n");
        prompt.append("  * Less formal connectors\n");
        prompt.append("  * Natural thought transitions\n");
        prompt.append("- FORBIDDEN:\n");
        prompt.append("  * Grammatical errors\n");
        prompt.append("  * Spelling errors\n");
        prompt.append("- No feeling that the text is \"too good\"\n\n");
        
        prompt.append("IMPORTANT:\n");
        prompt.append("- Do NOT improve me as a candidate\n");
        prompt.append("- Do NOT add skills or experience\n");
        prompt.append("- Work ONLY with what is already in the text\n");
        prompt.append("- The text should feel natural and authentic, not polished to perfection\n\n");
        
        prompt.append("Use all previous steps to generate the cover letter.\n");
        prompt.append("When generating the final letter, do NOT mirror the analytical structure of steps 1–5.\n");
        prompt.append("The text must follow a natural human narrative flow, not a requirement-by-requirement structure.\n\n");
        
        prompt.append("Specify:\n");
        
        // Language-specific specifications
        if ("de".equals(language)) {
            prompt.append("- Language: GERMAN (formal business style, DIN 5008 standard) - THE FINAL LETTER MUST BE IN GERMAN!\n");
            prompt.append("- Length: 1 page / 3-4 paragraphs (approx. 250-400 words)\n");
            prompt.append("- Structure:\n");
            prompt.append("  1. Betreff (Subject line): MUST include the job title \"").append(job.getPosition()).append("\"\n");
            prompt.append("  2. Introduction paragraph: Reference to position, professional intent, high fit\n");
            prompt.append("  3. Qualification paragraph(s): Mapping job requirements → candidate qualifications with concrete examples\n");
            prompt.append("  4. Motivation & Cultural Fit: Why the company, not just the role; connection of personal values with organizational mission\n");
            prompt.append("  5. Closing paragraph: Readiness for interview, formal German closing etiquette\n");
            prompt.append("  6. Signature: \"Mit freundlichen Grüßen\" followed by candidate's name\n\n");
            prompt.append("- Formatting: Follow DIN 5008 standard for German business letters\n");
            prompt.append("- Style: Formal, professional, business-like throughout\n\n");
        } else if ("ru".equals(language)) {
            prompt.append("- Language: RUSSIAN - THE FINAL LETTER MUST BE IN RUSSIAN!\n");
            prompt.append("- Length: 1 page / 3-4 paragraphs (approx. 300-450 words)\n");
            prompt.append("- Structure:\n");
            prompt.append("  1. Greeting: Appropriate Russian business greeting\n");
            prompt.append("  2. Introduction paragraph: Reference to position, strong motivation, personal interest\n");
            prompt.append("  3. Qualification paragraph(s): Mapping job requirements → candidate qualifications with concrete examples, emphasizing motivation\n");
            prompt.append("  4. Motivation & Personal Fit: Strong emphasis on why the company and role, personal values alignment\n");
            prompt.append("  5. Closing paragraph: Readiness for interview, warm but professional closing\n");
            prompt.append("  6. Signature: Appropriate Russian closing followed by candidate's name\n\n");
            prompt.append("- Formatting: Follow Russian business letter standards\n");
            prompt.append("- Style: Warmer tone, strong emphasis on motivation, balance of professionalism and personal touch\n\n");
        } else { // "en"
            prompt.append("- Language: BRITISH ENGLISH - THE FINAL LETTER MUST BE IN BRITISH ENGLISH!\n");
            prompt.append("- Length: 1 page / 3-4 paragraphs (approx. 300-400 words)\n");
            prompt.append("- Structure:\n");
            prompt.append("  1. Greeting: Appropriate British business greeting\n");
            prompt.append("  2. Introduction paragraph: Reference to position, professional intent, balanced approach\n");
            prompt.append("  3. Qualification paragraph(s): Mapping job requirements → candidate qualifications with concrete examples\n");
            prompt.append("  4. Motivation & Cultural Fit: Why the company, balance of professionalism and personality\n");
            prompt.append("  5. Closing paragraph: Readiness for interview, professional British closing\n");
            prompt.append("  6. Signature: Appropriate British closing followed by candidate's name\n\n");
            prompt.append("- Formatting: Follow UK business letter standards\n");
            prompt.append("- Style: Balance between professionalism and personality, professional yet approachable\n");
            prompt.append("- Spelling: Use British English spelling (e.g., 'organise' not 'organize', 'colour' not 'color')\n\n");
        }
        
        prompt.append("- Usage:\n");
        prompt.append("  * Job title: \"").append(job.getPosition()).append("\" MUST be mentioned in the text\n");
        prompt.append("  * Company name: \"").append(job.getCompany()).append("\" MUST be mentioned in the text\n");
        
        // Make salutation conditional on user wishes and language
        if (wishes != null && !wishes.trim().isEmpty()) {
            prompt.append("  * Salutation: FOLLOW USER WISHES ABOVE - if user specified a different salutation, use that instead of default\n");
        } else {
            if ("de".equals(language)) {
                prompt.append("  * Salutation: \"Sehr geehrte Damen und Herren\" (Standard German greeting)\n");
            } else if ("ru".equals(language)) {
                prompt.append("  * Salutation: Appropriate Russian business greeting (e.g., \"Уважаемые господа\" or \"Здравствуйте\")\n");
            } else { // "en"
                prompt.append("  * Salutation: Appropriate British business greeting (e.g., \"Dear Sir/Madam\" or \"Dear Hiring Manager\")\n");
            }
        }
        if ("de".equals(language)) {
            prompt.append("  * Closing: \"Mit freundlichen Grüßen\" followed by the candidate's name\n");
        } else if ("ru".equals(language)) {
            prompt.append("  * Closing: \"С уважением\" or appropriate Russian closing followed by the candidate's name\n");
        } else { // "en"
            prompt.append("  * Closing: \"Yours sincerely\" or appropriate British closing followed by the candidate's name\n");
        }
        prompt.append("  * CRITICAL - Candidate's name (MUST be used):\n");
        prompt.append("    - The name is in the CANDIDATE BIOGRAPHY section under \"Name:\"\n");
        prompt.append("    - You MUST use this name EXACTLY and COMPLETELY in the closing at the end\n");
        prompt.append("    - If the CV says \"Konstantin Glazunov\", use EXACTLY \"Konstantin Glazunov\", not just \"Konstantin\"\n");
        prompt.append("    - NO placeholders like [Name], [Kandidatenname], [Ihr Name] or similar - USE THE REAL NAME\n");
        prompt.append("    - The name MUST be on a new line after \"Mit freundlichen Grüßen\"\n");
        prompt.append("    - IF the name is NOT in the biography, then write \"Mit freundlichen Grüßen\" and leave a blank line\n");
        prompt.append("    - BUT: In 99% of cases the name is in the biography - use it!\n\n");
        
        prompt.append("- Prohibitions - ABSOLUTE PROHIBITION OF INVENTED INFORMATION:\n");
        if (wishes != null && !wishes.trim().isEmpty()) {
            String wishesLower = wishes.toLowerCase();
            boolean hasNegativeStatement = wishesLower.contains("нет опыта") || wishesLower.contains("no experience") || 
                                         wishesLower.contains("keine erfahrung") || wishesLower.contains("никогда не работал") ||
                                         wishesLower.contains("never worked") || wishesLower.contains("nie gearbeitet") ||
                                         wishesLower.contains("не работал") || wishesLower.contains("didn't work") ||
                                         wishesLower.contains("не имею") || wishesLower.contains("don't have");
            
            if (hasNegativeStatement) {
                prompt.append("  * CRITICAL: User has explicitly denied experience/qualifications - apply negative statements ONLY to explicitly denied role/field\n");
                prompt.append("  * If user denies something specific (e.g., \"never worked as X\") → exclude ONLY that specific role/field\n");
                prompt.append("  * If denial is ambiguous → interpret conservatively: assume role-specific unless explicitly absolute\n");
                prompt.append("  * If denial is absolute and explicit (e.g., \"no work experience at all\") → exclude all professional experience\n");
                prompt.append("  * Do NOT erase unrelated experience from biography if denial is not explicitly absolute\n");
            }
            prompt.append("  * EXCEPTION: If USER WISHES request something that contradicts biography, treat wishes as factual correction and follow them\n");
        }
        if (wishes != null && !wishes.trim().isEmpty()) {
            prompt.append("  * NO invented facts - use ONLY what is in the CANDIDATE BIOGRAPHY and explicit factual statements in USER WISHES\n");
        } else {
            prompt.append("  * NO invented facts - use ONLY what is in the CANDIDATE BIOGRAPHY\n");
        }
        if (wishes != null && !wishes.trim().isEmpty()) {
            String wishesLower = wishes.toLowerCase();
            boolean hasNegativeStatement = wishesLower.contains("нет опыта") || wishesLower.contains("no experience") || 
                                         wishesLower.contains("keine erfahrung") || wishesLower.contains("никогда не работал") ||
                                         wishesLower.contains("never worked") || wishesLower.contains("nie gearbeitet") ||
                                         wishesLower.contains("не работал") || wishesLower.contains("didn't work") ||
                                         wishesLower.contains("не имею") || wishesLower.contains("don't have");
            if (hasNegativeStatement) {
                prompt.append("  * SPECIAL RULE FOR NEGATIVE STATEMENTS: If user denies experience, apply denial ONLY to explicitly denied role/field\n");
                prompt.append("    - If denial is role-specific → exclude only that role, other experience can be used\n");
                prompt.append("    - If denial is absolute → exclude all professional experience\n");
                prompt.append("    - Do NOT erase unrelated experience if denial is not explicitly absolute\n");
            }
        }
        prompt.append("  * NO changing MEANING: if it says \"unvollständige Ausbildung\" (incomplete education), you must NOT write \"abgeschlossenen Ausbildung\" (completed education)\n");
        prompt.append("    Reformulation for natural text is ALLOWED, but meaning must be preserved\n");
        prompt.append("    UNLESS: User wishes explicitly request to change or omit this information - then follow wishes\n");
        prompt.append("  * NO invented experiences: if there is no experience with agricultural machinery in the CV, you must NOT write \"langjährige Erfahrung im Bereich Landmaschinen\" (extensive experience in agricultural machinery)\n");
        prompt.append("    UNLESS: User wishes explicitly request to mention or emphasize something different - then follow wishes\n");
        prompt.append("  * NO interpretations or additions - preserve MEANING from the biography\n");
        prompt.append("    Reformulation for coherence is ALLOWED, but do NOT enhance or embellish\n");
        prompt.append("    UNLESS: User wishes request different formulations or emphasis - then follow wishes\n");
        prompt.append("  * ONLY use matched CV data - they MUST be explicitly in the biography\n");
        prompt.append("    UNLESS: User wishes request to omit, change, or emphasize differently - then follow wishes\n");
        prompt.append("  * NO placeholders like [Name], [Kandidatenname], [Ihr Name], [Unternehmen] or similar\n");
        prompt.append("  * NO meta-commentary or process explanations\n");
        prompt.append("  * NO headings like \"Anschreiben\" or \"Motivationsschreiben\"\n");
        prompt.append("  * NO apologies or hints about missing data\n");
        prompt.append("  * NO omitting the name - the name MUST be present and from the biography\n");
        if (wishes != null && !wishes.trim().isEmpty()) {
            prompt.append("    UNLESS: User wishes explicitly request different name handling - then follow wishes\n");
        }
        prompt.append("\n");
        
        prompt.append("Instruction:\n");
        if (wishes != null && !wishes.trim().isEmpty()) {
            String wishesLower = wishes.toLowerCase();
            boolean hasNegativeStatement = wishesLower.contains("нет опыта") || wishesLower.contains("no experience") || 
                                         wishesLower.contains("keine erfahrung") || wishesLower.contains("никогда не работал") ||
                                         wishesLower.contains("never worked") || wishesLower.contains("nie gearbeitet") ||
                                         wishesLower.contains("не работал") || wishesLower.contains("didn't work") ||
                                         wishesLower.contains("не имею") || wishesLower.contains("don't have");
            
            if (hasNegativeStatement) {
                prompt.append("CRITICAL INSTRUCTION FOR NEGATIVE STATEMENTS:\n");
                prompt.append("The user has explicitly stated they DO NOT have experience or have NEVER worked.\n");
                prompt.append("Apply negative statements ONLY to the explicitly denied role, field, or qualification.\n\n");
                prompt.append("INTERPRETATION:\n");
                prompt.append("- If denial is role-specific (e.g., \"never worked as developer\") → exclude ONLY that role, other experience can be used\n");
                prompt.append("- If denial is absolute and explicit (e.g., \"no work experience at all\") → exclude all professional experience\n");
                prompt.append("- If ambiguous → prefer conservative interpretation: assume role-specific unless explicitly absolute\n");
                prompt.append("- Do NOT erase unrelated experience from biography if denial is not explicitly absolute\n\n");
                prompt.append("DO NOT mention, reference, or imply any work experience that the user explicitly denies.\n");
                prompt.append("Instead, focus on:\n");
                prompt.append("- Education and training\n");
                prompt.append("- Motivation and willingness to learn\n");
                prompt.append("- Transferable skills from other areas (if not explicitly denied)\n");
                prompt.append("- Other work experience from biography (if not explicitly denied)\n");
                prompt.append("- Personal qualities and enthusiasm\n");
                prompt.append("- Honest acknowledgment of lack of experience with emphasis on learning ability\n\n");
                prompt.append("EXAMPLE APPROACH:\n");
                prompt.append("NOTE: The following examples are semantic illustrations only. ");
                prompt.append("The final output MUST still be in the target language (");
                if ("ru".equals(language)) {
                    prompt.append("RUSSIAN");
                } else if ("en".equals(language)) {
                    prompt.append("BRITISH ENGLISH");
                } else {
                    prompt.append("GERMAN");
                }
                prompt.append(").\n\n");
                prompt.append("If user says \"Я никогда не работал разработчиком\" (I never worked as developer):\n");
                prompt.append("✓ Write: [Semantic: acknowledge lack of experience in the denied field, express motivation]\n");
                prompt.append("✓ Write: [Semantic: despite lack of experience in the field, show willingness to learn]\n");
                prompt.append("✓ Focus on education, courses, projects, motivation\n");
                prompt.append("✓ ALLOWED: Other work experience from biography (e.g., if user worked as tester, manager, etc.)\n");
                prompt.append("❌ DO NOT write: [Semantic: do not mention current position in the denied field with experience]\n");
                prompt.append("❌ DO NOT mention any developer positions, even if in biography\n");
                prompt.append("❌ DO NOT imply professional experience in the denied field\n\n");
            } else {
                prompt.append("Integrate explicit + implied requirements with the candidate's relevant experiences.\n");
                prompt.append("Use experiences and qualifications from CANDIDATE BIOGRAPHY and explicit factual statements in USER WISHES.\n");
                prompt.append("If user wishes contradict biography, treat wishes as factual correction and follow them.\n");
                prompt.append("Do NOT infer, assume, or extend beyond these sources.\n");
            }
        } else {
            prompt.append("Integrate explicit + implied requirements with the candidate's relevant experiences.\n");
            prompt.append("BUT: Use ONLY experiences and qualifications that are ACTUALLY in the CANDIDATE BIOGRAPHY.\n");
            prompt.append("Do NOT infer, assume, or extend beyond the biography.\n");
        }
        prompt.append("If something is NOT in the CV, accept it - do NOT invent and do NOT rephrase.\n");
        if (wishes != null && !wishes.trim().isEmpty()) {
            String wishesLower = wishes.toLowerCase();
            boolean hasNegativeStatement = wishesLower.contains("нет опыта") || wishesLower.contains("no experience") || 
                                         wishesLower.contains("keine erfahrung") || wishesLower.contains("никогда не работал") ||
                                         wishesLower.contains("never worked") || wishesLower.contains("nie gearbeitet") ||
                                         wishesLower.contains("не работал") || wishesLower.contains("didn't work") ||
                                         wishesLower.contains("не имею") || wishesLower.contains("don't have");
            if (!hasNegativeStatement) {
                prompt.append("UNLESS: User wishes explicitly request to mention or emphasize something - then follow wishes.\n");
            }
        }
        prompt.append("Use a professional German business tone.\n");
        prompt.append("The text must be ready to send.\n\n");
        
        // === MASTER CONSISTENCY RULE ===
        prompt.append("MASTER CONSISTENCY RULE:\n\n");
        prompt.append("If the same limitation or prohibition is mentioned multiple times in this prompt,\n");
        prompt.append("it applies in its strictest version, but does not require repeated confirmation in the text.\n");
        prompt.append("This rule ensures consistency: all prohibitions (e.g., \"do NOT invent facts\", \"do NOT add experience\")\n");
        prompt.append("apply throughout the entire generation process, even if not explicitly restated.\n\n");
        
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
        if ("ru".equals(language)) {
            prompt.append("- ONLY the final cover letter in RUSSIAN language\n");
        } else if ("en".equals(language)) {
            prompt.append("- ONLY the final cover letter in BRITISH ENGLISH language\n");
        } else {
            prompt.append("- ONLY the final cover letter in GERMAN language\n");
        }
        if ("de".equals(language)) {
            prompt.append("- The text starts directly with the subject line (Betreff) or salutation\n");
        } else {
            prompt.append("- The text starts directly with the salutation\n");
        }
        prompt.append("- The text ends with THE REAL NAME from the biography after the closing\n");
        prompt.append("- The name MUST be taken from the section \"CANDIDATE BIOGRAPHY\" → \"Name:\"\n");
        prompt.append("- The text is completely ready to send\n\n");
        
        // Reinforce user wishes in OUTPUT section
        if (wishes != null && !wishes.trim().isEmpty()) {
            prompt.append("FINAL REMINDER - USER WISHES MUST BE APPLIED:\n");
            prompt.append("Before generating the final output, check again: Did you follow ALL user wishes?\n");
            prompt.append("User wishes: \"").append(wishes.trim()).append("\"\n");
            prompt.append("If the user requested specific changes (e.g., different salutation, tone, content), ");
            prompt.append("these MUST be reflected in the final output.\n\n");
            
            String wishesLower = wishes.toLowerCase();
            boolean hasNegativeStatement = wishesLower.contains("нет опыта") || wishesLower.contains("no experience") || 
                                         wishesLower.contains("keine erfahrung") || wishesLower.contains("никогда не работал") ||
                                         wishesLower.contains("never worked") || wishesLower.contains("nie gearbeitet") ||
                                         wishesLower.contains("не работал") || wishesLower.contains("didn't work") ||
                                         wishesLower.contains("не имею") || wishesLower.contains("don't have");
            
            if (hasNegativeStatement) {
                prompt.append("🚨🚨🚨 CRITICAL FINAL CHECK FOR NEGATIVE STATEMENTS 🚨🚨🚨\n");
                prompt.append("The user explicitly stated they DO NOT have experience or have NEVER worked.\n");
                prompt.append("BEFORE OUTPUTTING, VERIFY:\n");
                prompt.append("1. Did I correctly interpret the denial?\n");
                prompt.append("   - If denial was role-specific → Did I exclude ONLY that role? (Other experience can remain)\n");
                prompt.append("   - If denial was absolute → Did I exclude all professional experience? (Education/training can remain)\n");
                prompt.append("   - If denial was ambiguous → Did I use conservative interpretation? (Did NOT erase unrelated experience)\n");
                prompt.append("2. Did I mention ANY work experience in the explicitly denied role/field? → If YES, REMOVE IT\n");
                prompt.append("3. Did I write phrases about denied experience (e.g., \"In meiner Position als X...\")? → If YES, REMOVE IT\n");
                prompt.append("4. Did I imply professional experience in the denied field? → If YES, REMOVE IT\n");
                prompt.append("5. Did I preserve unrelated experience from biography (if denial was not absolute)? → If NO, CHECK AGAIN\n");
                prompt.append("6. Did I focus on education, motivation, learning ability? → If NO, ADD IT\n");
                prompt.append("7. Did I write honestly about lack of experience in the denied field? → If NO, ADD IT\n\n");
                prompt.append("REMEMBER: Negative statements apply ONLY to explicitly denied role/field.\n");
                prompt.append("NOTE: The following examples are semantic illustrations only. ");
                prompt.append("The final output MUST still be in the target language (");
                if ("ru".equals(language)) {
                    prompt.append("RUSSIAN");
                } else if ("en".equals(language)) {
                    prompt.append("BRITISH ENGLISH");
                } else {
                    prompt.append("GERMAN");
                }
                prompt.append(").\n\n");
                prompt.append("If user says \"Я никогда не работал разработчиком\" (I never worked as developer):\n");
                prompt.append("❌ DO NOT write: [Semantic: do not mention current position in the denied field with experience]\n");
                prompt.append("✓ ALLOWED: Other work experience from biography (if not explicitly denied)\n");
                prompt.append("✓ Write: [Semantic: acknowledge lack of experience in the denied field] or similar honest statement.\n\n");
            }
            
            prompt.append("CONFLICT RESOLUTION CHECK:\n");
            prompt.append("If user wishes CONTRADICT information from CANDIDATE BIOGRAPHY, apply the Conflict Resolution Rule:\n");
            prompt.append("User wishes may override biography facts ONLY in the form of explicit factual corrections or explicit fact exclusions.\n");
            prompt.append("User wishes may NEVER be used to invent new facts, roles, experience, skills, or timelines.\n");
            prompt.append("Example: If biography suggests formal tone but user wishes say \"use informal tone\", use informal tone.\n");
            prompt.append("Example: If biography contains certain skills but user wishes say \"don't mention X\", don't mention X.\n");
            prompt.append("Example: If biography has certain experience but user wishes say \"emphasize Y instead\", emphasize Y.\n");
            if (hasNegativeStatement) {
                prompt.append("Example: If user says \"I never worked as X\" but biography shows X experience, DO NOT mention X experience.\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("PROCESS:\n");
        prompt.append("1. Perform steps 1-5 INTERNALLY (do not show in output)\n");
        prompt.append("2. Use the results of steps 1-5 to create the cover letter\n");
        if (wishes != null && !wishes.trim().isEmpty()) {
            prompt.append("2a. CRITICAL: Apply user wishes/corrections from STEP 6 - they override standard conventions\n");
        }
        if ("ru".equals(language)) {
            prompt.append("3. Output ONLY the completed cover letter (Step 6) - IN RUSSIAN!\n\n");
        } else if ("en".equals(language)) {
            prompt.append("3. Output ONLY the completed cover letter (Step 6) - IN BRITISH ENGLISH!\n\n");
        } else {
            prompt.append("3. Output ONLY the completed cover letter (Step 6) - IN GERMAN!\n\n");
        }
        
        prompt.append("EXAMPLE of correct output (with real name from biography):\n");
        
        // Language-specific example structure
        if ("de".equals(language)) {
            prompt.append("Betreff: Bewerbung als ").append(job.getPosition() != null ? job.getPosition() : "[Job Title]").append("\n\n");
            
            // Show example that respects user wishes
            if (wishes != null && !wishes.trim().isEmpty()) {
                prompt.append("(IMPORTANT: If user wishes specify a different salutation, use that instead of the default below)\n");
                String wishesLower = wishes.toLowerCase();
                if (wishesLower.contains("hallo") || wishesLower.contains("вместо") && wishesLower.contains("hallo")) {
                    prompt.append("Hallo,\n\n");
                    prompt.append("(Example shows 'Hallo' because user requested it - follow user wishes exactly)\n\n");
                } else {
                    prompt.append("Sehr geehrte Damen und Herren,\n\n");
                    prompt.append("(Note: If user wishes specify a different salutation, use that instead)\n\n");
                }
            } else {
                prompt.append("Sehr geehrte Damen und Herren,\n\n");
            }
        } else if ("ru".equals(language)) {
            prompt.append("(Russian business greeting, e.g., \"Уважаемые господа\" or \"Здравствуйте\")\n\n");
        } else { // "en"
            prompt.append("(British business greeting, e.g., \"Dear Sir/Madam\" or \"Dear Hiring Manager\")\n\n");
        }
        if ("ru".equals(language)) {
            prompt.append("[Cover letter text here - 3-4 paragraphs, ONLY with facts from the biography, written in RUSSIAN]\n\n");
        } else if ("en".equals(language)) {
            prompt.append("[Cover letter text here - 3-4 paragraphs, ONLY with facts from the biography, written in BRITISH ENGLISH]\n\n");
        } else {
            prompt.append("[Cover letter text here - 3-4 paragraphs, ONLY with facts from the biography, written in GERMAN]\n\n");
        }
        if ("ru".equals(language)) {
            prompt.append("С уважением\n");
        } else if ("en".equals(language)) {
            prompt.append("Yours sincerely\n");
        } else {
            prompt.append("Mit freundlichen Grüßen\n");
        }
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
    
    /**
     * Internal class to represent a categorized wish
     */
    private static class WishCategory {
        String category;
        String text;
        
        WishCategory(String category, String text) {
            this.category = category;
            this.text = text;
        }
    }
    
    /**
     * Categorizes user wishes into three types:
     * 1. FACTUAL_CORRECTION - explicit corrections of facts
     * 2. FACT_EXCLUSION - exclusions/denials (starts with "я не", "я никогда", "не используй", etc.)
     * 3. STYLE_INSTRUCTION - style and presentation instructions
     * 
     * Supports explicit markers (FACTUAL_CORRECTION:, FACT_EXCLUSION:, STYLE_INSTRUCTION:)
     * or automatic detection based on patterns.
     */
    private WishCategory categorizeWish(String wishText) {
        if (wishText == null || wishText.trim().isEmpty()) {
            return new WishCategory("STYLE_INSTRUCTION", wishText);
        }
        
        String trimmed = wishText.trim();
        String lower = trimmed.toLowerCase();
        
        // Check for explicit markers first
        if (lower.startsWith("factual_correction:") || lower.startsWith("factual correction:")) {
            String text = trimmed.substring(trimmed.indexOf(':') + 1).trim();
            return new WishCategory("FACTUAL_CORRECTION", text);
        }
        if (lower.startsWith("fact_exclusion:") || lower.startsWith("fact exclusion:")) {
            String text = trimmed.substring(trimmed.indexOf(':') + 1).trim();
            return new WishCategory("FACT_EXCLUSION", text);
        }
        if (lower.startsWith("style_instruction:") || lower.startsWith("style instruction:")) {
            String text = trimmed.substring(trimmed.indexOf(':') + 1).trim();
            return new WishCategory("STYLE_INSTRUCTION", text);
        }
        
        // Automatic detection based on patterns
        // FACT_EXCLUSION patterns: "я не", "я никогда", "не используй", "don't", "не упоминай", etc.
        String[] exclusionPatterns = {
            "я не", "я никогда", "не используй", "не упоминай", "не пиши", "не говори",
            "don't", "never", "no", "not", "exclude", "не включай", "не добавляй",
            "keine", "nicht", "nie", "не имею", "нет опыта", "no experience", "keine erfahrung"
        };
        
        for (String pattern : exclusionPatterns) {
            // Check if pattern is at the start of the string or surrounded by spaces
            if (lower.startsWith(pattern) || 
                lower.contains(" " + pattern + " ") ||
                lower.endsWith(" " + pattern) ||
                lower.startsWith(pattern + " ")) {
                return new WishCategory("FACT_EXCLUSION", trimmed);
            }
        }
        
        // FACTUAL_CORRECTION patterns: "вместо", "instead", "correct", "исправь", "замени"
        String[] correctionPatterns = {
            "вместо", "instead", "correct", "исправь", "замени", "replace", "change",
            "statt", "anstatt", "измени", "правка"
        };
        
        for (String pattern : correctionPatterns) {
            // Check if pattern is at the start of the string or surrounded by spaces
            if (lower.startsWith(pattern) || 
                lower.contains(" " + pattern + " ") ||
                lower.endsWith(" " + pattern) ||
                lower.startsWith(pattern + " ")) {
                return new WishCategory("FACTUAL_CORRECTION", trimmed);
            }
        }
        
        // Default to STYLE_INSTRUCTION for everything else
        return new WishCategory("STYLE_INSTRUCTION", trimmed);
    }
}
