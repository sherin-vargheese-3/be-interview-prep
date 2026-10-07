package com.edstem.interviewprep.config;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.random.RandomGenerator;
import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /** Short codes must not be guessable from previous ones, so use a CSPRNG. */
  @Bean
  RandomGenerator codeRandom() {
    return new SecureRandom();
  }

  /**
   * Makes time-based constraints ({@code @FutureOrPresent}, {@code @Future}) use the injected
   * {@link Clock}, so tests can control "now".
   */
  @Bean
  ValidationConfigurationCustomizer clockAwareValidation(Clock clock) {
    return configuration -> configuration.clockProvider(() -> clock);
  }
}
