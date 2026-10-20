package com.platform;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test database: a PostgreSQL 18 container, or an existing database when bk.test.external-db is set (for machines
 * without Docker).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/**
	 * A fresh PostgreSQL in Docker per application context - the default. Where Docker is
	 * unavailable (or in CI with a database service), run with
	 * {@code -Dbk.test.external-db=true -Dbk.test.db.url=... -Dbk.test.db.username=...
	 * -Dbk.test.db.password=...} to use that PostgreSQL instead: an empty database of the
	 * same major version, which every test context then migrates and shares. Dedicated
	 * names, not DB_URL: a DB_URL system property would also override the configuration
	 * contract tests' controlled environment.
	 */
	@Bean
	@ServiceConnection
	@ConditionalOnProperty(name = "bk.test.external-db", havingValue = "false", matchIfMissing = true)
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
	@ConditionalOnProperty(name = "bk.test.external-db", havingValue = "true")
	DynamicPropertyRegistrar externalDatabaseProperties() {

		return registry -> {
			registry.add("spring.datasource.url", () -> System.getProperty("bk.test.db.url"));
			registry.add("spring.datasource.username", () -> System.getProperty("bk.test.db.username"));
			registry.add("spring.datasource.password", () -> System.getProperty("bk.test.db.password"));
		};

	}

	@Bean
	DynamicPropertyRegistrar mailServerProperties(GreenMail greenMail) {

		return registry -> registry.add("spring.mail.port", () -> greenMail.getSmtp().getPort());

	}

}
