package com.bewerbung;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BewerbungAiApplication {

    public static void main(String[] args) {
        // Проверка переменной окружения GPT_API_KEY
        String apiKey = System.getenv("GPT_API_KEY");
        System.out.println("GPT_API_KEY: " + apiKey);
        SpringApplication.run(BewerbungAiApplication.class, args);
    }
}

