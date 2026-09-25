package com.tokenpilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TokenPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TokenPilotApplication.class, args);
    }
}
