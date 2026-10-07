package com.edstem.interviewprep.model;

import com.edstem.interviewprep.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code passwordHash} is a BCrypt hash; the raw password is never stored. Emails are stored
 * lower-cased and are unique at the database level, so two concurrent registrations can't both
 * succeed. (Table is {@code app_users} because {@code user} is a reserved word in SQL.)
 */
@Entity
@Table(
    name = "app_users",
    uniqueConstraints = @UniqueConstraint(name = "uk_app_users_email", columnNames = "email"))
public class User {

  @Id private UUID id;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Role role;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected User() {}

  public User(
      UUID id, String email, String name, String passwordHash, Role role, Instant createdAt) {
    this.id = id;
    this.email = email;
    this.name = name;
    this.passwordHash = passwordHash;
    this.role = role;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getName() {
    return name;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  /** Keeps the hash out of logs and debugger output. */
  @Override
  public String toString() {
    return "User[id=%s, email=%s, role=%s]".formatted(id, email, role);
  }
}
