package com.edstem.interviewprep.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edstem.interviewprep.InterviewPrepApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** No secret is hard-coded, so the app must refuse to start rather than run with a weak one. */
class StartupSecretTest {

  @Test
  void missingSecret_failsStartupWithClearMessage() {
    assertThatThrownBy(() -> start("--app.jwt.secret="))
        .rootCause()
        .hasMessageContaining(
            "app.jwt.secret is required: set the JWT_SECRET environment variable");
  }

  @Test
  void shortSecret_failsStartup() {
    assertThatThrownBy(() -> start("--app.jwt.secret=too-short"))
        .rootCause()
        .hasMessageContaining("must be at least 32 characters");
  }

  private static void start(String secretArg) {
    new SpringApplicationBuilder(InterviewPrepApplication.class)
        .web(WebApplicationType.NONE)
        .run(secretArg, "--spring.main.banner-mode=off")
        .close();
  }
}
