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
public class GroqAiService {

    private static final Logger logger = LoggerFactory.getLogger(GroqAiService.class);
    
    private final WebClient webClient;
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final Gson gson;

    public GroqAiService(@Value("${groq.api.key}") String apiKey,
                         @Value("${groq.api.url}") String apiUrl,
                         @Value("${groq.model}") String model) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.gson = new Gson();
        this.webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .build();
    }

    public String generateText(String prompt) {
        logger.info("Generating text with Groq API...");
        
        try {
            JsonObject requestBody = buildRequestBody(prompt);
            String requestBodyJson = gson.toJson(requestBody);
            
            logger.debug("Sending request to Groq API: {}", apiUrl);
            
            String responseJson = webClient.post()
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBodyJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            if (responseJson == null) {
                throw new RuntimeException("Empty response from Groq API");
            }
            
            logger.debug("Received response from Groq API");
            String generatedText = extractTextFromResponse(responseJson);
            
            logger.info("Successfully generated text (length: {} characters)", generatedText.length());
            return generatedText;
            
        } catch (Exception e) {
            logger.error("Error generating text with Groq API", e);
            throw new RuntimeException("Failed to generate text with Groq API: " + e.getMessage(), e);
        }
    }

    private JsonObject buildRequestBody(String prompt) {
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
                logger.error("No choices in Groq API response");
                throw new RuntimeException("No choices in Groq API response");
            }
            
            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject message = choice.getAsJsonObject("message");
            
            if (message == null) {
                logger.error("No message in Groq API response");
                throw new RuntimeException("No message in Groq API response");
            }
            
            String text = message.get("content").getAsString();
            
            return text;
            
        } catch (Exception e) {
            logger.error("Error parsing Groq API response: {}", responseJson, e);
            throw new RuntimeException("Failed to parse Groq API response: " + e.getMessage(), e);
        }
    }
}

