package com.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * The Bookeyond Kid API: one Spring Boot application holding every module (identity, families, learning, content,
 * schools, plans and payments). Configuration records are found by scanning; scheduling runs the periodic sweeps
 * (erasure, payments).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LearningPlatformApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(LearningPlatformApiApplication.class, args);
	}

}
