package com.bewerbung.config;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class EnvConfig {

    private static final Logger logger = LoggerFactory.getLogger(EnvConfig.class);
    private static Dotenv dotenv;

    @PostConstruct
    public void loadEnvFile() {
        try {
            dotenv = Dotenv.configure()
                    .filename("variables.env")
                    .load();
            
            // Set system properties from .env file (if not already set as environment variables)
            for (DotenvEntry entry : dotenv.entries()) {
                String key = entry.getKey();
                String value = entry.getValue();
                
                // Only set as system property if not already set as environment variable
                if (System.getenv(key) == null) {
                    System.setProperty(key, value);
                }
            }
            
            logger.info("Successfully loaded environment variables from variables.env");
            logger.debug("Loaded {} variables from variables.env", dotenv.entries().size());
            
        } catch (Exception e) {
            logger.warn("Could not load variables.env file: {}", e.getMessage());
            logger.debug("This is okay if variables are set as system environment variables");
        }
    }

    /**
     * Get a value from the loaded .env file
     * @param key The environment variable key
     * @return The value, or null if not found
     */
    public static String get(String key) {
        if (dotenv != null) {
            return dotenv.get(key);
        }
        return null;
    }

    /**
     * Get a value from the loaded .env file with a default value
     * @param key The environment variable key
     * @param defaultValue Default value if not found
     * @return The value or default value
     */
    public static String get(String key, String defaultValue) {
        if (dotenv != null) {
            String value = dotenv.get(key);
            return value != null ? value : defaultValue;
        }
        return defaultValue;
    }
}

