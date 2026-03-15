package com.pricingtool.test.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Validated
@ConfigurationProperties(prefix = "test.env")
public class TestEnvironmentConfig {

    @NotBlank(message = "test.env.base-url must be set in application.yml")
    private final String baseUrl;

    public TestEnvironmentConfig(@NotBlank String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
