package com.edstem.interviewprep.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** There is deliberately no role field: self-registration always creates a USER. */
public record RegisterRequest(
    @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        @Size(max = 254, message = "email must be at most 254 characters")
        String email,
    // BCrypt only uses the first 72 bytes, so longer passwords would be silently truncated.
    @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
        @MaxUtf8Bytes(value = 72, message = "password must be at most 72 bytes in UTF-8")
        String password,
    @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name) {

  @Override
  public String toString() {
    return "RegisterRequest[email=%s, name=%s]".formatted(email, name);
  }
}
