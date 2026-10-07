package com.edstem.interviewprep.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bound from {@code app.jwt.*}. The secret is supplied via the {@code JWT_SECRET} environment
 * variable; the app refuses to start without one (HS256 needs at least 256 bits).
 */
@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(
    @NotBlank(message = "app.jwt.secret is required: set the JWT_SECRET environment variable")
        @Size(min = 32, message = "app.jwt.secret (JWT_SECRET) must be at least 32 characters")
        String secret,
    @NotNull Duration ttl,
    @NotBlank String issuer) {

  @Override
  public String toString() {
    return "JwtProperties[ttl=%s, issuer=%s]".formatted(ttl, issuer);
  }
}
