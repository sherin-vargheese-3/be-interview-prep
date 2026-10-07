package com.edstem.interviewprep.support;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base for tests that run the whole application (HTTP, validation, services, database) through
 * MockMvc. Subclasses share one Spring context. The clock is a {@link MutableClock} reset to {@link
 * #NOW} before each test, so time-dependent rules are deterministic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTest.ClockConfig.class)
public abstract class IntegrationTest {

  protected static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

  @TestConfiguration
  static class ClockConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock(NOW);
    }
  }

  @Autowired protected MockMvc mockMvc;
  @Autowired protected MutableClock clock;

  @BeforeEach
  void resetClock() {
    clock.set(NOW);
  }
}
