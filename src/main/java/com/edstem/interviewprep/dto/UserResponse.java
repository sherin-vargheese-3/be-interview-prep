package com.edstem.interviewprep.dto;

import com.edstem.interviewprep.enums.Role;
import com.edstem.interviewprep.model.User;
import java.time.Instant;
import java.util.UUID;

/** Public view of a user: never includes the password hash. */
public record UserResponse(UUID id, String email, String name, Role role, Instant createdAt) {

  public static UserResponse from(User user) {
    return new UserResponse(
        user.getId(), user.getEmail(), user.getName(), user.getRole(), user.getCreatedAt());
  }
}
