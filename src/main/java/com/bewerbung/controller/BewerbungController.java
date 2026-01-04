package com.bewerbung.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bewerbung")
public class BewerbungController {

    @GetMapping("/health")
    public String health() {
        return "Bewerbung AI Service is running";
    }
}

