package com.tokenpilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tokenpilot")
public record TokenPilotProperties(Llm llm) {

    public record Llm(String apiKey, String baseUrl, String model) {}
}
