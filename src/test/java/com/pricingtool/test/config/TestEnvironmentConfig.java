package com.pricingtool.test.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "test.env")
public class TestEnvironmentConfig {

    private final String name;

    @NotBlank(message = "test.env.base-url must be set in application.yml")
    private final String baseUrl;

    public TestEnvironmentConfig(String name, @NotBlank String baseUrl) {
        this.name = name;
        this.baseUrl = baseUrl;
    }

    public String getName() {
        return name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
