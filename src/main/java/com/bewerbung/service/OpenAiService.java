package com.bewerbung.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class OpenAiService {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiService.class);
    
    private final WebClient webClient;
    private final String apiKey;
    private final String apiUrl;
    private final String lightModel;
    private final String heavyModel;
    private final Gson gson;

    public OpenAiService(@Value("#{systemEnvironment['GPT_API_KEY']}") String apiKey,
                         @Value("${openai.api.url}") String apiUrl,
                         @Value("${openai.model.light}") String lightModel,
                         @Value("${openai.model.heavy}") String heavyModel) {
                            
                                if (apiKey == null || apiKey.isBlank() || apiKey.isEmpty()) {
                                    throw new IllegalStateException("GPT_API_KEY is missing or empty");
                                }
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.lightModel = lightModel;
        this.heavyModel = heavyModel;
        this.gson = new Gson();
        this.webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .build();
        
        logger.info("OpenAI Service initialized - Light model: {}, Heavy model: {}", lightModel, heavyModel);
    }

    /**
     * Generate text using the light model (for analysis tasks)
     */
    public String generateTextWithLightModel(String prompt) {
        logger.info("Generating text with OpenAI API using LIGHT model ({})...", lightModel);
        return generateText(prompt, lightModel);
    }

    /**
     * Generate text using the heavy model (for final document generation)
     */
    public String generateTextWithHeavyModel(String prompt) {
        logger.info("Generating text with OpenAI API using HEAVY model ({})...", heavyModel);
        return generateText(prompt, heavyModel);
    }

    /**
     * @deprecated Use generateTextWithLightModel() or generateTextWithHeavyModel() instead
     */
    @Deprecated
    public String generateText(String prompt) {
        logger.warn("Using deprecated generateText() method - defaulting to heavy model");
        return generateTextWithHeavyModel(prompt);
    }

    private String generateText(String prompt, String model) {
        try {
            JsonObject requestBody = buildRequestBody(prompt, model);
            String requestBodyJson = gson.toJson(requestBody);
            
            logger.debug("Sending request to OpenAI API: {} with model: {}", apiUrl, model);
            
            String responseJson = webClient.post()
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBodyJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            if (responseJson == null) {
                throw new RuntimeException("Empty response from OpenAI API");
            }
            
            logger.debug("Received response from OpenAI API");
            String generatedText = extractTextFromResponse(responseJson);
            
            logger.info("Successfully generated text with {} (length: {} characters)", model, generatedText.length());
            return generatedText;
            
        } catch (Exception e) {
            logger.error("Error generating text with OpenAI API using model: {}", model, e);
            throw new RuntimeException("Failed to generate text with OpenAI API: " + e.getMessage(), e);
        }
    }

    private JsonObject buildRequestBody(String prompt, String model) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        
        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);
        
        requestBody.add("messages", messages);
        
        return requestBody;
    }

    private String extractTextFromResponse(String responseJson) {
        try {
            JsonObject jsonResponse = gson.fromJson(responseJson, JsonObject.class);
            JsonArray choices = jsonResponse.getAsJsonArray("choices");
            
            if (choices == null || choices.size() == 0) {
                logger.error("No choices in OpenAI API response");
                throw new RuntimeException("No choices in OpenAI API response");
            }
            
            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject message = choice.getAsJsonObject("message");
            
            if (message == null) {
                logger.error("No message in OpenAI API response");
                throw new RuntimeException("No message in OpenAI API response");
            }
            
            String text = message.get("content").getAsString();
            
            return text;
            
        } catch (Exception e) {
            logger.error("Error parsing OpenAI API response: {}", responseJson, e);
            throw new RuntimeException("Failed to parse OpenAI API response: " + e.getMessage(), e);
        }
    }
}

