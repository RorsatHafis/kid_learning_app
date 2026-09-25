package com.platform;

import org.springframework.boot.SpringApplication;

public class TestLearningPlatformApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(LearningPlatformApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
