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
public class GeminiAiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiAiService.class);
    
    private final WebClient webClient;
    private final String apiKey;
    private final String apiUrl;
    private final Gson gson;

    public GeminiAiService(@Value("${gemini.api.key}") String apiKey,
                          @Value("${gemini.api.url}") String apiUrl) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.gson = new Gson();
        this.webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .build();
    }

    public String generateText(String prompt) {
        logger.info("Generating text with Gemini API...");
        
        try {
            JsonObject requestBody = buildRequestBody(prompt);
            String requestBodyJson = gson.toJson(requestBody);
            
            logger.debug("Sending request to Gemini API: {}", apiUrl);
            
            String responseJson = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("key", apiKey)
                            .build())
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBodyJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            if (responseJson == null) {
                throw new RuntimeException("Empty response from Gemini API");
            }
            
            logger.debug("Received response from Gemini API");
            String generatedText = parseResponse(responseJson);
            
            logger.info("Successfully generated text (length: {} characters)", generatedText.length());
            return generatedText;
            
        } catch (Exception e) {
            logger.error("Error generating text with Gemini API", e);
            throw new RuntimeException("Failed to generate text with Gemini API: " + e.getMessage(), e);
        }
    }

    private JsonObject buildRequestBody(String prompt) {
        JsonObject requestBody = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        
        part.addProperty("text", prompt);
        parts.add(part);
        content.add("parts", parts);
        contents.add(content);
        requestBody.add("contents", contents);
        
        return requestBody;
    }

    private String parseResponse(String responseJson) {
        try {
            JsonObject jsonResponse = gson.fromJson(responseJson, JsonObject.class);
            JsonArray candidates = jsonResponse.getAsJsonArray("candidates");
            
            if (candidates == null || candidates.size() == 0) {
                logger.error("No candidates in Gemini API response");
                throw new RuntimeException("No candidates in Gemini API response");
            }
            
            JsonObject candidate = candidates.get(0).getAsJsonObject();
            JsonObject content = candidate.getAsJsonObject("content");
            JsonArray parts = content.getAsJsonArray("parts");
            
            if (parts == null || parts.size() == 0) {
                logger.error("No parts in Gemini API response");
                throw new RuntimeException("No parts in Gemini API response");
            }
            
            JsonObject part = parts.get(0).getAsJsonObject();
            String text = part.get("text").getAsString();
            
            return text;
            
        } catch (Exception e) {
            logger.error("Error parsing Gemini API response: {}", responseJson, e);
            throw new RuntimeException("Failed to parse Gemini API response: " + e.getMessage(), e);
        }
    }
}

