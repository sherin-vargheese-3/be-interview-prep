package com.edstem.interviewprep.service;

import com.edstem.interviewprep.dto.LoginRequest;
import com.edstem.interviewprep.dto.TokenResponse;
import com.edstem.interviewprep.exception.InvalidCredentialsException;
import com.edstem.interviewprep.model.User;
import com.edstem.interviewprep.repository.UserRepository;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private final UserRepository repository;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokenService;

  /** Compared against when the email is unknown, so both failure paths take the same time. */
  private final String dummyHash;

  public AuthService(
      UserRepository repository, PasswordEncoder passwordEncoder, TokenService tokenService) {
    this.repository = repository;
    this.passwordEncoder = passwordEncoder;
    this.tokenService = tokenService;
    this.dummyHash = passwordEncoder.encode("timing-equaliser-not-a-real-password");
  }

  /**
   * Unknown email and wrong password give the same error (and similar timing), so the endpoint
   * can't be used to discover which emails are registered.
   */
  public TokenResponse login(LoginRequest request) {
    Optional<User> user = repository.findByEmail(UserService.normalizeEmail(request.email()));
    String hash = user.map(User::getPasswordHash).orElse(dummyHash);
    boolean matches = passwordEncoder.matches(request.password(), hash);
    if (user.isEmpty() || !matches) {
      throw new InvalidCredentialsException();
    }
    return tokenService.issue(user.get());
  }
}
