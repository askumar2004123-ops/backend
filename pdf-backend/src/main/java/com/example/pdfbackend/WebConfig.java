package com.example.pdfbackend;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // This allows your Flutter Web app to talk to the backend securely
        registry.addMapping("/**")
                .allowedOriginPatterns("*") // Allows any frontend URL (Render, Firebase, localhost)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition") // Crucial for Flutter to read the downloaded filename
                .allowCredentials(false);
    }
}