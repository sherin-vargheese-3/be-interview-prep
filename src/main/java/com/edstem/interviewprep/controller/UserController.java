package com.edstem.interviewprep.controller;

import com.edstem.interviewprep.dto.UserResponse;
import com.edstem.interviewprep.service.UserService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Access rules live in {@code SecurityConfig}: {@code /me} any user, the list ADMIN only. */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  /** The caller's identity comes from the verified token, never from a request parameter. */
  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
    return UserResponse.from(userService.getById(UUID.fromString(jwt.getSubject())));
  }

  @GetMapping
  public List<UserResponse> listAll() {
    return userService.listAll().stream().map(UserResponse::from).toList();
  }
}
