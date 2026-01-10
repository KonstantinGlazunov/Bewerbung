package com.bewerbung;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BewerbungAiApplication {

    public static void main(String[] args) {
        // Load environment variables from variables.env file before Spring Boot starts
        // This ensures variables are available when Spring components are initialized
        try {
            Dotenv dotenv = Dotenv.configure()
                    .filename("variables.env")
                    .load();
            
            // Set system properties from .env file (if not already set as environment variables)
            int loadedCount = 0;
            for (var entry : dotenv.entries()) {
                String key = entry.getKey();
                String value = entry.getValue();
                // Only set if not already set as system environment variable
                if (System.getenv(key) == null) {
                    System.setProperty(key, value);
                    loadedCount++;
                }
            }
            
            System.out.println("✓ Loaded " + loadedCount + " environment variables from variables.env");
        } catch (Exception e) {
            System.out.println("⚠ Warning: Could not load variables.env file");
            System.out.println("   Current working directory: " + System.getProperty("user.dir"));
            System.out.println("   Error: " + e.getMessage());
            System.out.println("   Using system environment variables instead.");
            System.out.println("   Make sure variables.env exists in the project root directory.");
        }
        
        SpringApplication.run(BewerbungAiApplication.class, args);
    }
}

