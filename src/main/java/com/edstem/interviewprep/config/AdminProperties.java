package com.edstem.interviewprep.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound from {@code app.admin.*} ({@code ADMIN_EMAIL}, {@code ADMIN_PASSWORD}); both optional. */
@ConfigurationProperties("app.admin")
public record AdminProperties(String email, String password) {

  public boolean isConfigured() {
    return email != null && !email.isBlank() && password != null && !password.isBlank();
  }

  @Override
  public String toString() {
    return "AdminProperties[email=%s]".formatted(email);
  }
}
