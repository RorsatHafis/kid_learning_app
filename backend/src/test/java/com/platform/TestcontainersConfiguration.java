package com.platform;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {

		return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
		
	}

	/**
	 * A real SMTP server inside the test JVM, one per application context, on a free
	 * port. Mail is delivered to it over SMTP exactly as to a production relay, and
	 * tests read what actually arrived.
	 */
	@Bean(destroyMethod = "stop")
	GreenMail greenMail() {

		GreenMail greenMail = new GreenMail(ServerSetupTest.SMTP.dynamicPort());
		greenMail.start();
		return greenMail;

	}

	@Bean
	DynamicPropertyRegistrar mailServerProperties(GreenMail greenMail) {

		return registry -> registry.add("spring.mail.port", () -> greenMail.getSmtp().getPort());

	}

}
