package com.edstem.interviewprep.service;

import com.edstem.interviewprep.dto.RegisterRequest;
import com.edstem.interviewprep.enums.Role;
import com.edstem.interviewprep.exception.EmailAlreadyRegisteredException;
import com.edstem.interviewprep.exception.UserNotFoundException;
import com.edstem.interviewprep.model.User;
import com.edstem.interviewprep.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final UserRepository repository;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public UserService(UserRepository repository, PasswordEncoder passwordEncoder, Clock clock) {
    this.repository = repository;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  /**
   * Self-registration: always a USER, whatever the client sends. The existence check gives a fast,
   * clear 409; the unique constraint catches the race where two requests register the same email at
   * the same moment.
   */
  @Transactional
  public User register(RegisterRequest request) {
    String email = normalizeEmail(request.email());
    if (repository.existsByEmail(email)) {
      throw new EmailAlreadyRegisteredException(email);
    }
    try {
      return repository.saveAndFlush(newUser(email, request.password(), request.name(), Role.USER));
    } catch (DataIntegrityViolationException e) {
      throw new EmailAlreadyRegisteredException(email);
    }
  }

  /** Used to seed the admin; returns {@code false} when the email is already registered. */
  @Transactional
  public boolean createIfAbsent(String email, String password, String name, Role role) {
    String normalized = normalizeEmail(email);
    if (repository.existsByEmail(normalized)) {
      return false;
    }
    repository.save(newUser(normalized, password, name, role));
    return true;
  }

  @Transactional(readOnly = true)
  public User getById(UUID id) {
    return repository.findById(id).orElseThrow(UserNotFoundException::new);
  }

  @Transactional(readOnly = true)
  public List<User> listAll() {
    return repository.findAllByOrderByCreatedAtAscEmailAsc();
  }

  /** Emails are compared case-insensitively, so they are stored lower-cased. */
  public static String normalizeEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }

  private User newUser(String email, String password, String name, Role role) {
    return new User(
        UUID.randomUUID(),
        email,
        name.strip(),
        passwordEncoder.encode(password),
        role,
        Instant.now(clock));
  }
}
