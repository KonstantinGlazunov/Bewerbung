package com.bewerbung;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BewerbungAiApplication {

    private static final Logger logger = LoggerFactory.getLogger(BewerbungAiApplication.class);

    public static void main(String[] args) {
        // Disable PDFBox font cache to prevent crashes on Oracle Linux with problematic system fonts
        // This prevents PDFBox from scanning system fonts that may have missing CFF tables
        System.setProperty("org.apache.pdfbox.fontcache.disabled", "true");
        logger.info("PDFBox font cache disabled to prevent system font scanning issues");
        
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
            
            logger.info("Loaded {} environment variables from variables.env (only those not already set in OS env)", loadedCount);
        } catch (Exception e) {
            logger.info("Could not load variables.env; falling back to OS environment variables. Reason: {}", e.getMessage());
            logger.debug("variables.env load failure details", e);
        }

        // Spring Boot reads "spring.profiles.active", not SPRING_PROFILES_ACTIVE from system properties
        String profiles = System.getenv("SPRING_PROFILES_ACTIVE");
        if (profiles == null || profiles.isEmpty()) {
            profiles = System.getProperty("SPRING_PROFILES_ACTIVE");
        }
        if (profiles != null && !profiles.isEmpty()) {
            System.setProperty("spring.profiles.active", profiles);
        }

        // Auto-activate Oracle profile when Oracle URL is set → session data will be stored in DB and persist after session end
        String oracleUrl = System.getenv("ORACLE_JDBC_URL");
        if (oracleUrl == null || oracleUrl.isEmpty()) {
            oracleUrl = System.getProperty("ORACLE_JDBC_URL");
        }
        if (oracleUrl != null && !oracleUrl.isEmpty()) {
            String active = System.getProperty("spring.profiles.active");
            if (active == null || active.isEmpty()) {
                System.setProperty("spring.profiles.active", "oracle");
                logger.info("Oracle JDBC URL is set; activating profile 'oracle' so session data is stored in the database and persists after session end");
            } else if (!active.contains("oracle")) {
                System.setProperty("spring.profiles.active", active + ",oracle");
                logger.info("Oracle JDBC URL is set; adding profile 'oracle' so session data is stored in the database and persists after session end");
            }
        }

        // Oracle Autonomous DB wallet: TNS_ADMIN must be set before any JDBC connection
        String tnsAdmin = System.getenv("TNS_ADMIN");
        if (tnsAdmin == null || tnsAdmin.isEmpty()) {
            try {
                Dotenv d = Dotenv.configure().filename("variables.env").load();
                tnsAdmin = d.get("TNS_ADMIN");
            } catch (Exception ignored) { }
        }
        if (tnsAdmin != null && !tnsAdmin.isEmpty()) {
            System.setProperty("oracle.net.tns_admin", tnsAdmin);
            logger.info("Oracle TNS_ADMIN set from env/variables.env: {}", tnsAdmin);
        }

        SpringApplication.run(BewerbungAiApplication.class, args);
    }
}

