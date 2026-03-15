package com.pricingtool.test.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "test.env")
public class TestEnvironmentConfig {
    private String name;
    private String baseUrl;
}
