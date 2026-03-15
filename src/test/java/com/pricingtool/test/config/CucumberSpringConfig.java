package com.pricingtool.test.config;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@CucumberContextConfiguration
@SpringBootTest
@ConfigurationPropertiesScan("com.pricingtool.test.config")
@EntityScan("com.pricingtool.test")
@EnableJpaRepositories("com.pricingtool.test")
public class CucumberSpringConfig {
}
