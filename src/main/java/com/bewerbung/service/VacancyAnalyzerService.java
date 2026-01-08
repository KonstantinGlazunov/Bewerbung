package com.bewerbung.service;

import com.bewerbung.model.JobRequirements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class VacancyAnalyzerService {

    private static final Logger logger = LoggerFactory.getLogger(VacancyAnalyzerService.class);
    private static final String JOB_POSTING_PATH = "input/job_posting.txt";
    
    private final JobPostingAiAnalyzerService jobPostingAiAnalyzerService;

    @Autowired
    public VacancyAnalyzerService(JobPostingAiAnalyzerService jobPostingAiAnalyzerService) {
        this.jobPostingAiAnalyzerService = jobPostingAiAnalyzerService;
    }

    public JobRequirements analyzeVacancy() {
        logger.info("Starting vacancy analysis from file: {}", JOB_POSTING_PATH);
        
        String jobPostingText = readJobPostingFile();
        JobRequirements requirements = analyzeVacancy(jobPostingText);
        
        return requirements;
    }

    public JobRequirements analyzeVacancy(String jobPostingText) {
        logger.info("Starting vacancy analysis from provided text");
        
        JobRequirements requirements = null;
        
        try {
            // Primary method: Use AI to extract job requirements
            logger.info("Using AI-powered job posting analysis");
            requirements = jobPostingAiAnalyzerService.analyzeJobPosting(jobPostingText);
            
        } catch (Exception e) {
            logger.warn("AI-powered analysis failed, falling back to regex-based extraction", e);
            // Fallback: Use regex-based extraction
            requirements = extractRequirements(jobPostingText);
        }
        
        logExtractedRequirements(requirements);
        
        return requirements;
    }

    private String readJobPostingFile() {
        try {
            ClassPathResource resource = new ClassPathResource(JOB_POSTING_PATH);
            @SuppressWarnings("null")
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            return content;
        } catch (IOException e) {
            logger.error("Error reading job posting file: {}", JOB_POSTING_PATH, e);
            throw new RuntimeException("Failed to read job posting file", e);
        }
    }

    private JobRequirements extractRequirements(String text) {
        JobRequirements requirements = new JobRequirements();
        
        // Extract position
        String position = extractPosition(text);
        requirements.setPosition(position);
        
        // Extract company
        String company = extractCompany(text);
        requirements.setCompany(company);
        
        // Extract location
        String location = extractLocation(text);
        requirements.setLocation(location);
        
        // Extract education
        String education = extractEducation(text);
        requirements.setEducation(education);
        
        // Extract experience
        String experience = extractExperience(text);
        requirements.setExperience(experience);
        
        // Extract required skills
        List<String> requiredSkills = extractRequiredSkills(text);
        requirements.setRequiredSkills(requiredSkills);
        
        // Extract preferred skills
        List<String> preferredSkills = extractPreferredSkills(text);
        requirements.setPreferredSkills(preferredSkills);
        
        // Extract languages
        List<String> languages = extractLanguages(text);
        requirements.setLanguages(languages);
        
        return requirements;
    }

    private String extractPosition(String text) {
        Pattern pattern = Pattern.compile("Stellenausschreibung:\\s*(.+?)\\s*\\(m/w/d\\)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "Unknown Position";
    }

    private String extractCompany(String text) {
        Pattern pattern = Pattern.compile("Unternehmen:\\s*(.+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "Unknown Company";
    }

    private String extractLocation(String text) {
        Pattern pattern = Pattern.compile("Standort:\\s*(.+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "Unknown Location";
    }

    private String extractEducation(String text) {
        Pattern pattern = Pattern.compile("(?:Ihre Qualifikationen:|Qualifikationen:).*?-\\s*(Erfolgreich abgeschlossenes Hochschulstudium[^\\n]+)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "Not specified";
    }

    private String extractExperience(String text) {
        Pattern pattern = Pattern.compile("(Mehrjährige Berufserfahrung[^\\n]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "Not specified";
    }

    private List<String> extractRequiredSkills(String text) {
        List<String> skills = new ArrayList<>();
        
        // Extract skills from "Ihre Qualifikationen" section
        String qualificationsSection = extractSection(text, "Ihre Qualifikationen:", "Von Vorteil:");
        
        // Common technologies mentioned in the job posting
        String[] techKeywords = {
            "Java", "Spring Boot", "Spring Framework", "MySQL", "PostgreSQL",
            "REST-API", "REST API", "Git", "Maven", "Gradle"
        };
        
        for (String keyword : techKeywords) {
            if (qualificationsSection.toLowerCase().contains(keyword.toLowerCase())) {
                skills.add(keyword);
            }
        }
        
        // Extract specific skill mentions
        Pattern skillPattern = Pattern.compile("-\\s*(?:Fundierte Kenntnisse in|Kenntnisse in|Erfahrung mit)\\s*([^\\n]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = skillPattern.matcher(qualificationsSection);
        while (matcher.find()) {
            String skillLine = matcher.group(1).trim();
            // Clean up parentheses and extract main technologies
            skillLine = skillLine.replaceAll("\\([^)]+\\)", "").trim();
            if (!skillLine.isEmpty() && !skills.contains(skillLine)) {
                skills.add(skillLine);
            }
        }
        
        return skills;
    }

    private List<String> extractPreferredSkills(String text) {
        List<String> skills = new ArrayList<>();
        
        String preferredSection = extractSection(text, "Von Vorteil:", "Wir bieten:");
        
        // Common preferred technologies
        String[] preferredKeywords = {
            "Microservices", "AWS", "Azure", "Docker", "Kubernetes", "Cloud"
        };
        
        for (String keyword : preferredKeywords) {
            if (preferredSection.toLowerCase().contains(keyword.toLowerCase())) {
                skills.add(keyword);
            }
        }
        
        // Extract specific mentions
        Pattern skillPattern = Pattern.compile("-\\s*(?:Erfahrung mit|Kenntnisse in)\\s*([^\\n]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = skillPattern.matcher(preferredSection);
        while (matcher.find()) {
            String skillLine = matcher.group(1).trim();
            skillLine = skillLine.replaceAll("\\([^)]+\\)", "").trim();
            if (!skillLine.isEmpty() && !skills.contains(skillLine)) {
                skills.add(skillLine);
            }
        }
        
        return skills;
    }

    private List<String> extractLanguages(String text) {
        List<String> languages = new ArrayList<>();
        
        // Extract languages from text like "Gute Deutsch- und Englischkenntnisse"
        Pattern pattern = Pattern.compile("(?:Gute|Kenntnisse)\\s+([^\\-\\n]+?)(?:kenntnisse|kenntnis)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String langText = matcher.group(1).trim();
            // Extract languages (typically "Deutsch- und Englischkenntnisse")
            if (langText.contains("Deutsch") && langText.contains("Englisch")) {
                languages.add("Deutsch");
                languages.add("Englisch");
            } else if (langText.contains("Deutsch")) {
                languages.add("Deutsch");
            } else if (langText.contains("Englisch")) {
                languages.add("Englisch");
            }
        }
        
        // Fallback: simple pattern matching
        if (languages.isEmpty()) {
            if (text.toLowerCase().contains("deutsch")) languages.add("Deutsch");
            if (text.toLowerCase().contains("englisch")) languages.add("Englisch");
        }
        
        return languages;
    }

    private String extractSection(String text, String startMarker, String endMarker) {
        int startIndex = text.indexOf(startMarker);
        if (startIndex == -1) {
            return "";
        }
        
        int endIndex = text.indexOf(endMarker, startIndex);
        if (endIndex == -1) {
            endIndex = text.length();
        }
        
        return text.substring(startIndex, endIndex);
    }

    private void logExtractedRequirements(JobRequirements requirements) {
        logger.info("=== Extracted Job Requirements ===");
        logger.info("Position: {}", requirements.getPosition());
        logger.info("Company: {}", requirements.getCompany());
        logger.info("Location: {}", requirements.getLocation());
        logger.info("Education: {}", requirements.getEducation());
        logger.info("Experience: {}", requirements.getExperience());
        logger.info("Required Skills ({}): {}", 
            requirements.getRequiredSkills().size(), 
            String.join(", ", requirements.getRequiredSkills()));
        logger.info("Preferred Skills ({}): {}", 
            requirements.getPreferredSkills().size(), 
            String.join(", ", requirements.getPreferredSkills()));
        logger.info("Languages: {}", String.join(", ", requirements.getLanguages()));
        logger.info("===================================");
    }
}

