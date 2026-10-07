package com.edstem.interviewprep.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "email is required") String email,
    @NotBlank(message = "password is required") String password) {

  @Override
  public String toString() {
    return "LoginRequest[email=%s]".formatted(email);
  }
}
