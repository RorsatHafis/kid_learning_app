package com.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LearningPlatformApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(LearningPlatformApiApplication.class, args);
	}

}
