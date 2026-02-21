package com.bewerbung.service;

import com.bewerbung.model.Biography;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LebenslaufTemplateService {

    private static final Logger logger = LoggerFactory.getLogger(LebenslaufTemplateService.class);
    private static final String TEMPLATE_PATH = "lebenslauf.html";
    private static final String PHOTO_PATH = "/static/author-photo.jpg";
    
    private final MustacheFactory mustacheFactory;
    private final TempPhotoStorageService tempPhotoStorageService;

    public LebenslaufTemplateService(TempPhotoStorageService tempPhotoStorageService) {
        this.mustacheFactory = new DefaultMustacheFactory();
        this.tempPhotoStorageService = tempPhotoStorageService;
    }

    /**
     * Генерирует заполненный HTML на основе объекта Biography
     * @param biography объект Biography
     * @return заполненный HTML шаблон
     */
    public String generateLebenslauf(Biography biography) {
        logger.info("Generating lebenslauf from Biography object");
        
        // Преобразуем Biography в JsonObject
        JsonObject biographyJson = convertBiographyToJson(biography);
        return generateLebenslauf(biographyJson);
    }

    /**
     * Генерирует заполненный HTML на основе данных биографии
     * @param biographyJson JSON объект с данными биографии
     * @return заполненный HTML шаблон
     */
    public String generateLebenslauf(JsonObject biographyJson) {
        logger.info("Generating lebenslauf from biography data");
        
        if (biographyJson == null) {
            throw new IllegalArgumentException("Biography JSON cannot be null");
        }
        
        try {
            // Загружаем шаблон
            logger.debug("Loading template from: {}", TEMPLATE_PATH);
            String template = loadTemplate();
            logger.debug("Template loaded successfully ({} chars)", template.length());
            
            // Преобразуем данные для шаблона
            logger.debug("Mapping biography data to template data structure");
            Map<String, Object> templateData = mapBiographyToTemplateData(biographyJson);
            logger.debug("Template data mapped successfully. Keys: {}", templateData.keySet());
            
            // Рендерим шаблон
            logger.debug("Compiling Mustache template");
            Mustache mustache = mustacheFactory.compile(new StringReader(template), "lebenslauf");
            logger.debug("Template compiled successfully");
            
            logger.debug("Executing template with data");
            StringWriter writer = new StringWriter();
            mustache.execute(writer, templateData);
            
            String result = writer.toString();
            logger.info("Lebenslauf generated successfully ({} chars)", result.length());
            
            if (result.trim().isEmpty()) {
                logger.warn("Generated lebenslauf is empty!");
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error generating lebenslauf", e);
            throw new RuntimeException("Failed to generate lebenslauf: " + e.getMessage(), e);
        }
    }

    /**
     * Загружает шаблон из resources
     */
    private String loadTemplate() throws IOException {
        ClassPathResource resource = new ClassPathResource(TEMPLATE_PATH);
        if (!resource.exists()) {
            throw new RuntimeException("Template file not found: " + TEMPLATE_PATH);
        }
        try (InputStreamReader reader = new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8)) {
            StringBuilder content = new StringBuilder();
            char[] buffer = new char[8192];
            int length;
            while ((length = reader.read(buffer)) != -1) {
                content.append(buffer, 0, length);
            }
            return content.toString();
        }
    }

    /**
     * Преобразует данные биографии в структуру для Mustache шаблона
     */
    private Map<String, Object> mapBiographyToTemplateData(JsonObject biographyJson) {
        Map<String, Object> data = new HashMap<>();
        
        // Personal Information
        Map<String, Object> personalInfo = extractPersonalInformation(biographyJson);
        data.put("personalInformation", personalInfo);
        
        // Photo path (uploaded photo has priority over default)
        data.put("photoPath", resolvePhotoPath());
        
        // Profile text - оптимизируем для одной страницы
        String profileText = extractProfileText(biographyJson);
        profileText = optimizeProfileTextForOnePage(profileText);
        data.put("profileText", profileText);
        
        // Skills - оптимизируем для одной страницы
        Map<String, Object> skills = extractSkills(biographyJson);
        skills = optimizeSkillsForOnePage(skills);
        data.put("skills", skills);
        
        // Education - оптимизируем для одной страницы
        List<Map<String, Object>> education = extractEducation(biographyJson);
        education = optimizeEducationForOnePage(education);
        data.put("education", education);
        
        // Work Experience - оптимизируем для одной страницы
        List<Map<String, Object>> workExperience = extractWorkExperience(biographyJson);
        logger.debug("Work experience before optimization: {} items", workExperience != null ? workExperience.size() : 0);
        workExperience = optimizeWorkExperienceForOnePage(workExperience);
        logger.debug("Work experience after optimization: {} items", workExperience != null ? workExperience.size() : 0);
        data.put("workExperience", workExperience);
        
        return data;
    }

    /**
     * Извлекает персональную информацию
     */
    private Map<String, Object> extractPersonalInformation(JsonObject biographyJson) {
        Map<String, Object> personalInfo = new HashMap<>();
        
        JsonObject personalInfoObj = biographyJson.getAsJsonObject("personalInformation");
        if (personalInfoObj != null) {
            personalInfo.put("firstName", getStringValue(personalInfoObj, "firstName", ""));
            personalInfo.put("lastName", getStringValue(personalInfoObj, "lastName", ""));
            personalInfo.put("phone", getStringValue(personalInfoObj, "phone", ""));
            personalInfo.put("email", getStringValue(personalInfoObj, "email", ""));
            
            // Address
            JsonObject addressObj = personalInfoObj.getAsJsonObject("address");
            if (addressObj != null) {
                Map<String, Object> address = new HashMap<>();
                address.put("street", getStringValue(addressObj, "street", ""));
                address.put("postalCode", getStringValue(addressObj, "postalCode", ""));
                address.put("city", getStringValue(addressObj, "city", ""));
                address.put("country", getStringValue(addressObj, "country", ""));
                personalInfo.put("address", address);
            } else {
                // Пустой адрес если не указан
                Map<String, Object> address = new HashMap<>();
                address.put("street", "");
                address.put("postalCode", "");
                address.put("city", "");
                address.put("country", "");
                personalInfo.put("address", address);
            }
        }
        
        return personalInfo;
    }

    /**
     * Извлекает текст профиля (Profil)
     */
    private String extractProfileText(JsonObject biographyJson) {
        // Пытаемся извлечь из additionalInfo
        JsonObject additionalInfo = biographyJson.getAsJsonObject("additionalInfo");
        if (additionalInfo != null && additionalInfo.has("profile")) {
            return getStringValue(additionalInfo, "profile", "");
        }
        
        // Генерируем краткое описание на основе опыта работы
        JsonArray workExp = biographyJson.getAsJsonArray("workExperience");
        if (workExp != null && workExp.size() > 0) {
            StringBuilder profile = new StringBuilder();
            profile.append("Erfahrener Softwareentwickler mit umfangreicher Expertise in der Entwicklung ");
            profile.append("von Web-Anwendungen und Backend-Systemen. ");
            profile.append("Spezialisiert auf moderne Technologien und agile Entwicklungsmethoden.");
            return profile.toString();
        }
        
        return "Motivierter und engagierter Softwareentwickler mit Leidenschaft für innovative Lösungen.";
    }

    /**
     * Извлекает навыки (Kompetenzen)
     */
    private Map<String, Object> extractSkills(JsonObject biographyJson) {
        Map<String, Object> skills = new HashMap<>();
        
        JsonObject skillsObj = biographyJson.getAsJsonObject("skills");
        if (skillsObj != null) {
            // Programming Languages - оборачиваем в объект с hasItems и items
            JsonArray progLangs = extractItemsArray(skillsObj, "programmingLanguages");
            if (progLangs != null && progLangs.size() > 0) {
                List<String> languages = new ArrayList<>();
                for (int i = 0; i < progLangs.size(); i++) {
                    languages.add(progLangs.get(i).getAsString());
                }
                Map<String, Object> progLangWrapper = new HashMap<>();
                progLangWrapper.put("hasItems", true);
                progLangWrapper.put("items", languages);
                skills.put("programmingLanguages", progLangWrapper);
            }
            
            // Frameworks
            JsonArray frameworks = extractItemsArray(skillsObj, "frameworks");
            if (frameworks != null && frameworks.size() > 0) {
                List<String> frameworksList = new ArrayList<>();
                for (int i = 0; i < frameworks.size(); i++) {
                    frameworksList.add(frameworks.get(i).getAsString());
                }
                Map<String, Object> frameworksWrapper = new HashMap<>();
                frameworksWrapper.put("hasItems", true);
                frameworksWrapper.put("items", frameworksList);
                skills.put("frameworks", frameworksWrapper);
            }
            
            // Databases
            JsonArray databases = extractItemsArray(skillsObj, "databases");
            if (databases != null && databases.size() > 0) {
                List<String> databasesList = new ArrayList<>();
                for (int i = 0; i < databases.size(); i++) {
                    databasesList.add(databases.get(i).getAsString());
                }
                Map<String, Object> databasesWrapper = new HashMap<>();
                databasesWrapper.put("hasItems", true);
                databasesWrapper.put("items", databasesList);
                skills.put("databases", databasesWrapper);
            }
            
            // Tools
            JsonArray tools = extractItemsArray(skillsObj, "tools");
            if (tools != null && tools.size() > 0) {
                List<String> toolsList = new ArrayList<>();
                for (int i = 0; i < tools.size(); i++) {
                    toolsList.add(tools.get(i).getAsString());
                }
                Map<String, Object> toolsWrapper = new HashMap<>();
                toolsWrapper.put("hasItems", true);
                toolsWrapper.put("items", toolsList);
                skills.put("tools", toolsWrapper);
            }
            
            // Languages
            JsonArray languages = extractItemsArray(skillsObj, "languages");
            if (languages != null && languages.size() > 0) {
                List<Map<String, String>> languagesList = new ArrayList<>();
                for (int i = 0; i < languages.size(); i++) {
                    JsonObject langObj = languages.get(i).getAsJsonObject();
                    Map<String, String> lang = new HashMap<>();
                    lang.put("language", getStringValue(langObj, "language", ""));
                    lang.put("level", getStringValue(langObj, "level", ""));
                    languagesList.add(lang);
                }
                Map<String, Object> languagesWrapper = new HashMap<>();
                languagesWrapper.put("hasItems", true);
                languagesWrapper.put("items", languagesList);
                skills.put("languages", languagesWrapper);
            }
        }
        
        return skills;
    }

    /**
     * Извлекает образование (Bildung)
     */
    private List<Map<String, Object>> extractEducation(JsonObject biographyJson) {
        List<Map<String, Object>> educationList = new ArrayList<>();
        
        JsonArray educationArray = biographyJson.getAsJsonArray("education");
        if (educationArray != null) {
            for (int i = 0; i < educationArray.size(); i++) {
                JsonObject eduObj = educationArray.get(i).getAsJsonObject();
                Map<String, Object> education = new HashMap<>();
                
                education.put("degree", getStringValue(eduObj, "degree", ""));
                education.put("field", getStringValue(eduObj, "field", ""));
                education.put("university", getStringValue(eduObj, "university", ""));
                education.put("startDate", getStringValue(eduObj, "startDate", ""));
                education.put("endDate", getStringValue(eduObj, "endDate", ""));
                education.put("description", getStringValue(eduObj, "description", ""));
                
                educationList.add(education);
            }
        }
        
        return educationList;
    }

    /**
     * Извлекает опыт работы (Berufserfahrung)
     */
    private List<Map<String, Object>> extractWorkExperience(JsonObject biographyJson) {
        List<Map<String, Object>> workExpList = new ArrayList<>();
        
        JsonArray workExpArray = biographyJson.getAsJsonArray("workExperience");
        if (workExpArray != null) {
            logger.debug("Found {} work experience items in biography", workExpArray.size());
            for (int i = 0; i < workExpArray.size(); i++) {
                JsonObject workObj = workExpArray.get(i).getAsJsonObject();
                Map<String, Object> workExp = new HashMap<>();
                
                workExp.put("position", getStringValue(workObj, "position", ""));
                workExp.put("company", getStringValue(workObj, "company", ""));
                workExp.put("startDate", getStringValue(workObj, "startDate", ""));
                workExp.put("endDate", getStringValue(workObj, "endDate", ""));
                workExp.put("description", getStringValue(workObj, "description", ""));
                
                // Technologies - оборачиваем в объект с hasItems и items
                JsonArray technologies = extractItemsArray(workObj, "technologies");
                Map<String, Object> techWrapper = new HashMap<>();
                if (technologies != null && technologies.size() > 0) {
                    List<String> techList = new ArrayList<>();
                    for (int j = 0; j < technologies.size(); j++) {
                        techList.add(technologies.get(j).getAsString());
                    }
                    techWrapper.put("hasItems", true);
                    techWrapper.put("items", techList);
                } else {
                    // Создаем пустой объект для совместимости с шаблоном
                    techWrapper.put("hasItems", false);
                    techWrapper.put("items", new ArrayList<>());
                }
                workExp.put("technologies", techWrapper);
                
                workExpList.add(workExp);
                logger.debug("Extracted work experience item {}: position={}, company={}", 
                        i, workExp.get("position"), workExp.get("company"));
            }
        }
        
        return workExpList;
    }

    /**
     * Безопасное извлечение строкового значения из JsonObject
     */
    private String getStringValue(JsonObject obj, String key, String defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return defaultValue;
    }

    /**
     * Supports both plain arrays and wrapped format:
     * { "hasItems": true, "items": [ ... ] }
     */
    private JsonArray extractItemsArray(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        JsonElement element = obj.get(key);
        if (element.isJsonArray()) {
            return element.getAsJsonArray();
        }
        if (element.isJsonObject()) {
            JsonObject wrapper = element.getAsJsonObject();
            if (wrapper.has("items") && wrapper.get("items").isJsonArray()) {
                return wrapper.getAsJsonArray("items");
            }
        }
        return null;
    }

    private String resolvePhotoPath() {
        return tempPhotoStorageService.getCurrentPhotoDataUri().orElse(PHOTO_PATH);
    }

    /**
     * Оптимизирует текст профиля для одной страницы A4
     * Ограничивает длину текста профиля
     */
    private String optimizeProfileTextForOnePage(String profileText) {
        if (profileText == null || profileText.isEmpty()) {
            return profileText;
        }
        
        final int MAX_PROFILE_LENGTH = 250; // максимальная длина профиля в символах
        
        if (profileText.length() > MAX_PROFILE_LENGTH) {
            // Обрезаем до MAX_PROFILE_LENGTH и пытаемся обрезать по последнему предложению
            String truncated = profileText.substring(0, MAX_PROFILE_LENGTH).trim();
            int lastPeriod = truncated.lastIndexOf('.');
            if (lastPeriod > MAX_PROFILE_LENGTH * 0.7) {
                return truncated.substring(0, lastPeriod + 1);
            } else {
                return truncated + "...";
            }
        }
        
        return profileText;
    }

    /**
     * Оптимизирует навыки для одной страницы A4
     * Ограничивает количество навыков в каждой категории
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> optimizeSkillsForOnePage(Map<String, Object> skills) {
        if (skills == null) {
            return skills;
        }
        
        Map<String, Object> optimized = new HashMap<>(skills);
        
        // Максимальное количество навыков в каждой категории
        final int MAX_SKILLS_PER_CATEGORY = 5;
        
        // Оптимизируем каждую категорию навыков
        String[] skillCategories = {"programmingLanguages", "frameworks", "databases", "tools"};
        for (String category : skillCategories) {
            if (optimized.containsKey(category)) {
                Object categoryObj = optimized.get(category);
                if (categoryObj instanceof Map) {
                    Map<String, Object> categoryMap = (Map<String, Object>) categoryObj;
                    if (categoryMap.containsKey("items") && categoryMap.get("items") instanceof List) {
                        List<String> items = (List<String>) categoryMap.get("items");
                        if (items.size() > MAX_SKILLS_PER_CATEGORY) {
                            // Оставляем только первые MAX_SKILLS_PER_CATEGORY
                            List<String> optimizedItems = new ArrayList<>(items.subList(0, MAX_SKILLS_PER_CATEGORY));
                            Map<String, Object> optimizedCategory = new HashMap<>();
                            optimizedCategory.put("hasItems", !optimizedItems.isEmpty());
                            optimizedCategory.put("items", optimizedItems);
                            optimized.put(category, optimizedCategory);
                        }
                    }
                }
            }
        }
        
        // Языки обычно немного, но ограничим до 3
        if (optimized.containsKey("languages")) {
            Object languagesObj = optimized.get("languages");
            if (languagesObj instanceof Map) {
                Map<String, Object> languagesMap = (Map<String, Object>) languagesObj;
                if (languagesMap.containsKey("items") && languagesMap.get("items") instanceof List) {
                    List<Map<String, String>> items = (List<Map<String, String>>) languagesMap.get("items");
                    if (items.size() > 3) {
                        List<Map<String, String>> optimizedItems = new ArrayList<>(items.subList(0, 3));
                        Map<String, Object> optimizedLanguages = new HashMap<>();
                        optimizedLanguages.put("hasItems", !optimizedItems.isEmpty());
                        optimizedLanguages.put("items", optimizedItems);
                        optimized.put("languages", optimizedLanguages);
                    }
                }
            }
        }
        
        return optimized;
    }

    /**
     * Оптимизирует образование для одной страницы A4
     * Оставляет только последние или самые важные записи
     */
    private List<Map<String, Object>> optimizeEducationForOnePage(List<Map<String, Object>> education) {
        if (education == null || education.isEmpty()) {
            return education;
        }
        
        // Оставляем максимум 2 последних образования
        final int MAX_EDUCATION_ITEMS = 2;
        
        if (education.size() > MAX_EDUCATION_ITEMS) {
            // Берем последние MAX_EDUCATION_ITEMS (предполагаем, что они отсортированы от новых к старым)
            return new ArrayList<>(education.subList(0, MAX_EDUCATION_ITEMS));
        }
        
        return education;
    }

    /**
     * Оптимизирует опыт работы для одной страницы A4
     * Оставляет только последние и релевантные должности, сокращает описания
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> optimizeWorkExperienceForOnePage(List<Map<String, Object>> workExperience) {
        if (workExperience == null || workExperience.isEmpty()) {
            return workExperience;
        }
        
        // Оставляем максимум 3-4 последних должности
        final int MAX_WORK_EXPERIENCE_ITEMS = 3;
        final int MAX_DESCRIPTION_LENGTH = 150; // максимальная длина описания в символах
        final int MAX_TECHNOLOGIES = 4; // максимальное количество технологий
        
        logger.debug("Optimizing work experience: {} items found, max allowed: {}", 
                workExperience.size(), MAX_WORK_EXPERIENCE_ITEMS);
        
        List<Map<String, Object>> optimized = new ArrayList<>();
        
        // Берем первые MAX_WORK_EXPERIENCE_ITEMS (предполагаем, что они отсортированы от новых к старым)
        int itemsToTake = Math.min(workExperience.size(), MAX_WORK_EXPERIENCE_ITEMS);
        
        logger.debug("Taking {} items from work experience", itemsToTake);
        
        for (int i = 0; i < itemsToTake; i++) {
            Map<String, Object> workExp = new HashMap<>(workExperience.get(i));
            
            logger.debug("Processing work experience item {}: position={}, company={}", 
                    i, workExp.get("position"), workExp.get("company"));
            
            // Сокращаем описание, если оно слишком длинное
            if (workExp.containsKey("description")) {
                Object descObj = workExp.get("description");
                if (descObj instanceof String) {
                    String description = (String) descObj;
                    if (description.length() > MAX_DESCRIPTION_LENGTH) {
                        // Обрезаем до MAX_DESCRIPTION_LENGTH и добавляем "..."
                        description = description.substring(0, MAX_DESCRIPTION_LENGTH).trim();
                        // Пытаемся обрезать по последнему предложению
                        int lastPeriod = description.lastIndexOf('.');
                        if (lastPeriod > MAX_DESCRIPTION_LENGTH * 0.7) {
                            description = description.substring(0, lastPeriod + 1);
                        } else {
                            description += "...";
                        }
                        workExp.put("description", description);
                    }
                }
            }
            
            // Ограничиваем количество технологий
            if (workExp.containsKey("technologies")) {
                Object techObj = workExp.get("technologies");
                if (techObj instanceof Map) {
                    Map<String, Object> techMap = (Map<String, Object>) techObj;
                    if (techMap.containsKey("items") && techMap.get("items") instanceof List) {
                        List<String> techItems = (List<String>) techMap.get("items");
                        if (techItems.size() > MAX_TECHNOLOGIES) {
                            List<String> optimizedTech = new ArrayList<>(techItems.subList(0, MAX_TECHNOLOGIES));
                            Map<String, Object> optimizedTechMap = new HashMap<>();
                            optimizedTechMap.put("hasItems", !optimizedTech.isEmpty());
                            optimizedTechMap.put("items", optimizedTech);
                            workExp.put("technologies", optimizedTechMap);
                        }
                    }
                }
            } else {
                // Если technologies нет, создаем пустой объект для совместимости с шаблоном
                Map<String, Object> emptyTechMap = new HashMap<>();
                emptyTechMap.put("hasItems", false);
                emptyTechMap.put("items", new ArrayList<>());
                workExp.put("technologies", emptyTechMap);
            }
            
            optimized.add(workExp);
            logger.debug("Added work experience item {} to optimized list", i);
        }
        
        logger.debug("Optimized work experience: {} items in result", optimized.size());
        return optimized;
    }

    /**
     * Преобразует объект Biography в JsonObject в формате, ожидаемом шаблоном
     */
    private JsonObject convertBiographyToJson(Biography biography) {
        JsonObject json = new JsonObject();
        
        // Personal Information
        JsonObject personalInfo = new JsonObject();
        String name = biography.getName();
        if (name != null && !name.trim().isEmpty()) {
            String[] nameParts = name.split(" ", 2);
            personalInfo.addProperty("firstName", nameParts.length > 0 ? nameParts[0] : "");
            personalInfo.addProperty("lastName", nameParts.length > 1 ? nameParts[1] : "");
        } else {
            personalInfo.addProperty("firstName", "");
            personalInfo.addProperty("lastName", "");
        }
        personalInfo.addProperty("email", biography.getEmail() != null ? biography.getEmail() : "");
        personalInfo.addProperty("phone", biography.getPhone() != null ? biography.getPhone() : "");
        personalInfo.addProperty("birthDate", biography.getDateOfBirth() != null ? biography.getDateOfBirth() : "");
        personalInfo.addProperty("nationality", biography.getNationality() != null ? biography.getNationality() : "");
        
        // Address
        JsonObject address = new JsonObject();
        String addressStr = biography.getAddress();
        if (addressStr != null && !addressStr.trim().isEmpty()) {
            // Пытаемся разобрать адрес (простой парсинг)
            String[] parts = addressStr.split(",");
            if (parts.length >= 1) {
                address.addProperty("street", parts[0].trim());
            }
            if (parts.length >= 2) {
                String[] cityParts = parts[1].trim().split(" ", 2);
                if (cityParts.length >= 1) {
                    address.addProperty("postalCode", cityParts[0]);
                }
                if (cityParts.length >= 2) {
                    address.addProperty("city", cityParts[1]);
                } else if (parts.length >= 3) {
                    address.addProperty("city", parts[2].trim());
                }
            }
            if (parts.length >= 3) {
                address.addProperty("country", parts[parts.length - 1].trim());
            }
        } else {
            address.addProperty("street", "");
            address.addProperty("postalCode", "");
            address.addProperty("city", "");
            address.addProperty("country", "");
        }
        personalInfo.add("address", address);
        json.add("personalInformation", personalInfo);
        
        // Education
        JsonArray educationArray = new JsonArray();
        if (biography.getEducation() != null) {
            for (Biography.Education edu : biography.getEducation()) {
                JsonObject eduObj = new JsonObject();
                eduObj.addProperty("degree", edu.getDegree() != null ? edu.getDegree() : "");
                eduObj.addProperty("field", ""); // Biography.Education не имеет поля field
                eduObj.addProperty("university", edu.getInstitution() != null ? edu.getInstitution() : "");
                eduObj.addProperty("startDate", edu.getStartDate() != null ? edu.getStartDate() : "");
                eduObj.addProperty("endDate", edu.getEndDate() != null ? edu.getEndDate() : "");
                eduObj.addProperty("description", edu.getDescription() != null ? edu.getDescription() : "");
                educationArray.add(eduObj);
            }
        }
        json.add("education", educationArray);
        
        // Work Experience
        JsonArray workExpArray = new JsonArray();
        if (biography.getWorkExperience() != null) {
            for (Biography.WorkExperience workExp : biography.getWorkExperience()) {
                JsonObject workObj = new JsonObject();
                workObj.addProperty("position", workExp.getPosition() != null ? workExp.getPosition() : "");
                workObj.addProperty("company", workExp.getCompany() != null ? workExp.getCompany() : "");
                workObj.addProperty("startDate", workExp.getStartDate() != null ? workExp.getStartDate() : "");
                workObj.addProperty("endDate", workExp.getEndDate() != null ? workExp.getEndDate() : "");
                workObj.addProperty("description", workExp.getResponsibilities() != null ? workExp.getResponsibilities() : "");
                // Technologies извлекаем из responsibilities, если возможно
                JsonArray technologies = new JsonArray();
                if (workExp.getResponsibilities() != null && workExp.getResponsibilities().contains("Technologien:")) {
                    String techPart = workExp.getResponsibilities().substring(
                        workExp.getResponsibilities().indexOf("Technologien:") + "Technologien:".length()
                    ).trim();
                    String[] techs = techPart.split(",");
                    for (String tech : techs) {
                        technologies.add(tech.trim());
                    }
                }
                // Оборачиваем в объект с hasItems и items
                JsonObject techWrapper = new JsonObject();
                techWrapper.addProperty("hasItems", technologies.size() > 0);
                techWrapper.add("items", technologies);
                workObj.add("technologies", techWrapper);
                workExpArray.add(workObj);
            }
        }
        json.add("workExperience", workExpArray);
        
        // Skills - оборачиваем каждую категорию в объект с hasItems и items
        JsonObject skills = new JsonObject();
        JsonArray programmingLanguages = new JsonArray();
        JsonArray languages = new JsonArray();
        
        // Извлекаем технические навыки
        if (biography.getTechnicalSkills() != null) {
            for (String skill : biography.getTechnicalSkills()) {
                programmingLanguages.add(skill);
            }
        }
        JsonObject progLangWrapper = new JsonObject();
        progLangWrapper.addProperty("hasItems", programmingLanguages.size() > 0);
        progLangWrapper.add("items", programmingLanguages);
        skills.add("programmingLanguages", progLangWrapper);
        
        JsonObject frameworksWrapper = new JsonObject();
        frameworksWrapper.addProperty("hasItems", false);
        frameworksWrapper.add("items", new JsonArray());
        skills.add("frameworks", frameworksWrapper);
        
        JsonObject databasesWrapper = new JsonObject();
        databasesWrapper.addProperty("hasItems", false);
        databasesWrapper.add("items", new JsonArray());
        skills.add("databases", databasesWrapper);
        
        JsonObject toolsWrapper = new JsonObject();
        toolsWrapper.addProperty("hasItems", false);
        toolsWrapper.add("items", new JsonArray());
        skills.add("tools", toolsWrapper);
        
        // Languages
        if (biography.getLanguages() != null) {
            for (Biography.Language lang : biography.getLanguages()) {
                JsonObject langObj = new JsonObject();
                langObj.addProperty("language", lang.getLanguage() != null ? lang.getLanguage() : "");
                langObj.addProperty("level", lang.getLevel() != null ? lang.getLevel() : "");
                languages.add(langObj);
            }
        }
        JsonObject languagesWrapper = new JsonObject();
        languagesWrapper.addProperty("hasItems", languages.size() > 0);
        languagesWrapper.add("items", languages);
        skills.add("languages", languagesWrapper);
        json.add("skills", skills);
        
        return json;
    }
}

