package com.bewerbung.service;

import com.bewerbung.model.Biography;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BiographyFileAnalyzerService {

    private static final Logger logger = LoggerFactory.getLogger(BiographyFileAnalyzerService.class);

    public Biography parseBiographyFromFile(MultipartFile file) {
        logger.info("Parsing biography from file: {}", file.getOriginalFilename());
        
        // Validate file
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Biography file must not be empty");
        }
        
        try {
            // Read file content
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            
            if (content.trim().isEmpty()) {
                throw new IllegalArgumentException("Biography file content is empty");
            }
            
            // Parse content
            Biography biography = parseBiographyContent(content);
            
            logger.info("Successfully parsed biography from file for: {}", biography.getName());
            return biography;
            
        } catch (IOException e) {
            logger.error("Error reading biography file", e);
            throw new RuntimeException("Failed to read biography file: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid biography file content", e);
            throw e;
        } catch (Exception e) {
            logger.error("Error parsing biography file", e);
            throw new RuntimeException("Failed to parse biography file: " + e.getMessage(), e);
        }
    }

    private Biography parseBiographyContent(String content) {
        Biography biography = new Biography();
        
        // Parse personal information
        String name = extractField(content, "Name:");
        String email = extractField(content, "Email:");
        String phone = extractField(content, "Phone:");
        String address = extractField(content, "Address:");
        String dateOfBirth = extractField(content, "Date of Birth:");
        String nationality = extractField(content, "Nationality:");
        
        biography.setName(name);
        biography.setEmail(email);
        biography.setPhone(phone);
        biography.setAddress(address);
        biography.setDateOfBirth(dateOfBirth);
        biography.setNationality(nationality);
        
        // Parse education
        List<Biography.Education> educationList = parseEducation(content);
        biography.setEducation(educationList);
        
        // Parse work experience
        List<Biography.WorkExperience> workExpList = parseWorkExperience(content);
        biography.setWorkExperience(workExpList);
        
        // Parse technical skills
        List<String> technicalSkills = parseTechnicalSkills(content);
        biography.setTechnicalSkills(technicalSkills);
        
        // Parse soft skills (empty for now, can be extended)
        biography.setSoftSkills(new ArrayList<>());
        
        // Parse languages
        List<Biography.Language> languages = parseLanguages(content);
        biography.setLanguages(languages);
        
        // Parse certifications
        List<Biography.Certification> certifications = parseCertifications(content);
        biography.setCertifications(certifications);
        
        return biography;
    }

    private String extractField(String content, String fieldName) {
        Pattern pattern = Pattern.compile("^" + Pattern.quote(fieldName) + "\\s*(.+)$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private List<Biography.Education> parseEducation(String content) {
        List<Biography.Education> educationList = new ArrayList<>();
        
        // Extract education section
        String educationSection = extractSection(content, "=== EDUCATION ===", "=== WORK EXPERIENCE ===");
        if (educationSection.isEmpty()) {
            educationSection = extractSection(content, "EDUCATION", "WORK EXPERIENCE");
        }
        
        if (educationSection.isEmpty()) {
            return educationList;
        }
        
        // Split by double newline or "Position:" to get individual education entries
        String[] entries = educationSection.split("(?=\\n\\n|Position:|Degree:)");
        
        for (String entry : entries) {
            if (entry.trim().isEmpty() || entry.contains("===")) {
                continue;
            }
            
            Biography.Education education = new Biography.Education();
            education.setDegree(extractField(entry, "Degree:"));
            education.setInstitution(extractField(entry, "Institution:"));
            education.setLocation(extractField(entry, "Location:"));
            education.setStartDate(extractField(entry, "Start Date:"));
            education.setEndDate(extractField(entry, "End Date:"));
            education.setDescription(extractField(entry, "Description:"));
            
            if (!education.getDegree().isEmpty() || !education.getInstitution().isEmpty()) {
                educationList.add(education);
            }
        }
        
        return educationList;
    }

    private List<Biography.WorkExperience> parseWorkExperience(String content) {
        List<Biography.WorkExperience> workExpList = new ArrayList<>();
        
        // Extract work experience section
        String workExpSection = extractSection(content, "=== WORK EXPERIENCE ===", "=== TECHNICAL SKILLS ===");
        if (workExpSection.isEmpty()) {
            workExpSection = extractSection(content, "WORK EXPERIENCE", "TECHNICAL SKILLS");
        }
        
        if (workExpSection.isEmpty()) {
            return workExpList;
        }
        
        // Split by "Position:" to get individual work experience entries
        String[] entries = workExpSection.split("(?=Position:)");
        
        for (String entry : entries) {
            if (entry.trim().isEmpty() || entry.contains("===")) {
                continue;
            }
            
            Biography.WorkExperience workExp = new Biography.WorkExperience();
            workExp.setPosition(extractField(entry, "Position:"));
            workExp.setCompany(extractField(entry, "Company:"));
            workExp.setLocation(extractField(entry, "Location:"));
            workExp.setStartDate(extractField(entry, "Start Date:"));
            workExp.setEndDate(extractField(entry, "End Date:"));
            workExp.setResponsibilities(extractField(entry, "Responsibilities:"));
            
            if (!workExp.getPosition().isEmpty() || !workExp.getCompany().isEmpty()) {
                workExpList.add(workExp);
            }
        }
        
        return workExpList;
    }

    private List<String> parseTechnicalSkills(String content) {
        List<String> skills = new ArrayList<>();
        
        // Extract technical skills section
        String skillsSection = extractSection(content, "=== TECHNICAL SKILLS ===", "=== LANGUAGES ===");
        if (skillsSection.isEmpty()) {
            skillsSection = extractSection(content, "TECHNICAL SKILLS", "LANGUAGES");
        }
        
        if (skillsSection.isEmpty()) {
            return skills;
        }
        
        // Remove section header
        skillsSection = skillsSection.replaceAll("(?i)===?\\s*TECHNICAL SKILLS\\s*===?", "").trim();
        
        // Split by comma or newline
        String[] skillArray = skillsSection.split("[,\\n]");
        for (String skill : skillArray) {
            String trimmed = skill.trim();
            if (!trimmed.isEmpty()) {
                skills.add(trimmed);
            }
        }
        
        return skills;
    }

    private List<Biography.Language> parseLanguages(String content) {
        List<Biography.Language> languages = new ArrayList<>();
        
        // Extract languages section
        String languagesSection = extractSection(content, "=== LANGUAGES ===", "=== CERTIFICATIONS ===");
        if (languagesSection.isEmpty()) {
            languagesSection = extractSection(content, "LANGUAGES", "CERTIFICATIONS");
        }
        
        if (languagesSection.isEmpty()) {
            return languages;
        }
        
        // Remove section header
        languagesSection = languagesSection.replaceAll("(?i)===?\\s*LANGUAGES\\s*===?", "").trim();
        
        // Parse language entries (format: "Language: Level")
        String[] lines = languagesSection.split("\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            
            // Try to match "Language: Level" format
            Pattern pattern = Pattern.compile("^(.+?):\\s*(.+)$");
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                Biography.Language language = new Biography.Language();
                language.setLanguage(matcher.group(1).trim());
                language.setLevel(matcher.group(2).trim());
                languages.add(language);
            }
        }
        
        return languages;
    }

    private List<Biography.Certification> parseCertifications(String content) {
        List<Biography.Certification> certifications = new ArrayList<>();
        
        // Extract certifications section
        String certSection = extractSection(content, "=== CERTIFICATIONS ===", null);
        if (certSection.isEmpty()) {
            certSection = extractSection(content, "CERTIFICATIONS", null);
        }
        
        if (certSection.isEmpty()) {
            return certifications;
        }
        
        // Remove section header
        certSection = certSection.replaceAll("(?i)===?\\s*CERTIFICATIONS\\s*===?", "").trim();
        
        // Parse certification entries (format: "Name (Issuer, Date)" or "Name: Issuer, Date")
        String[] lines = certSection.split("\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("-")) {
                continue;
            }
            
            // Remove leading dash if present
            line = line.replaceFirst("^-\\s*", "");
            
            Biography.Certification cert = new Biography.Certification();
            
            // Try format: "Name (Issuer, Date)"
            Pattern pattern1 = Pattern.compile("^(.+?)\\s*\\(([^,]+),\\s*(.+)\\)$");
            Matcher matcher1 = pattern1.matcher(line);
            if (matcher1.find()) {
                cert.setName(matcher1.group(1).trim());
                cert.setIssuer(matcher1.group(2).trim());
                cert.setDate(matcher1.group(3).trim());
            } else {
                // Try format: "Name: Issuer, Date"
                Pattern pattern2 = Pattern.compile("^(.+?):\\s*(.+),\\s*(.+)$");
                Matcher matcher2 = pattern2.matcher(line);
                if (matcher2.find()) {
                    cert.setName(matcher2.group(1).trim());
                    cert.setIssuer(matcher2.group(2).trim());
                    cert.setDate(matcher2.group(3).trim());
                } else {
                    // Simple format: just name
                    cert.setName(line);
                    cert.setIssuer("");
                    cert.setDate("");
                }
            }
            
            if (!cert.getName().isEmpty()) {
                certifications.add(cert);
            }
        }
        
        return certifications;
    }

    private String extractSection(String content, String startMarker, String endMarker) {
        int startIndex = content.indexOf(startMarker);
        if (startIndex == -1) {
            // Try case-insensitive search
            Pattern pattern = Pattern.compile(Pattern.quote(startMarker), Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                startIndex = matcher.start();
            } else {
                return "";
            }
        }
        
        startIndex += startMarker.length();
        
        int endIndex = content.length();
        if (endMarker != null) {
            int endMarkerIndex = content.indexOf(endMarker, startIndex);
            if (endMarkerIndex != -1) {
                endIndex = endMarkerIndex;
            } else {
                // Try case-insensitive search for end marker
                Pattern pattern = Pattern.compile(Pattern.quote(endMarker), Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(content.substring(startIndex));
                if (matcher.find()) {
                    endIndex = startIndex + matcher.start();
                }
            }
        }
        
        return content.substring(startIndex, endIndex).trim();
    }
}

