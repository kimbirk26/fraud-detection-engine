package com.kim.fraudengine;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for full-stack integration tests. Starts the complete Spring application context
 * against real PostgreSQL and Kafka containers provided by {@link TestcontainersConfiguration}.
 *
 * <p>Annotated with {@code disabledWithoutDocker = true} so that the tests are silently skipped in
 * environments where Docker is unavailable, such as some CI runners.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {}
