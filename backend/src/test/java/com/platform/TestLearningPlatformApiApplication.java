package com.platform;

import org.springframework.boot.SpringApplication;

/**
 * Runs the application locally against a throwaway PostgreSQL container (development aid, not a test).
 */
public class TestLearningPlatformApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(LearningPlatformApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
