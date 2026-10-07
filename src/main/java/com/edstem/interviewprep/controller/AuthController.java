package com.edstem.interviewprep.controller;

import com.edstem.interviewprep.dto.LoginRequest;
import com.edstem.interviewprep.dto.RegisterRequest;
import com.edstem.interviewprep.dto.TokenResponse;
import com.edstem.interviewprep.dto.UserResponse;
import com.edstem.interviewprep.model.User;
import com.edstem.interviewprep.service.AuthService;
import com.edstem.interviewprep.service.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public endpoints: the only ones reachable without a token. */
@RestController
@Validated
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final UserService userService;
  private final AuthService authService;

  public AuthController(UserService userService, AuthService authService) {
    this.userService = userService;
    this.authService = authService;
  }

  @PostMapping("/register")
  public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
    User user = userService.register(request);
    return ResponseEntity.created(URI.create("/api/v1/users/me")).body(UserResponse.from(user));
  }

  @PostMapping("/login")
  public TokenResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }
}
