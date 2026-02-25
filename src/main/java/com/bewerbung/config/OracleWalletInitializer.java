package com.bewerbung.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Sets oracle.net.tns_admin from configuration when TNS_ADMIN env is not set.
 * Use spring.datasource.oracle.tns-admin in application.properties or profile.
 */
public class OracleWalletInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger log = LoggerFactory.getLogger(OracleWalletInitializer.class);

    @Override
    public void initialize(@NonNull ConfigurableApplicationContext applicationContext) {
        if (System.getProperty("oracle.net.tns_admin") != null) {
            return; // already set (e.g. from main() via TNS_ADMIN env)
        }
        Environment env = applicationContext.getEnvironment();
        String tnsAdmin = env.getProperty("spring.datasource.oracle.tns-admin");
        if (tnsAdmin != null && !tnsAdmin.isBlank()) {
            System.setProperty("oracle.net.tns_admin", tnsAdmin.trim());
            log.info("Oracle TNS_ADMIN set from config: {}", tnsAdmin);
        }
    }
}
