package com.edstem.interviewprep.model;

import com.edstem.interviewprep.enums.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tasks", indexes = @Index(name = "idx_tasks_status", columnList = "status"))
public class Task {

  @Id private UUID id;

  @Column(nullable = false, length = 100)
  private String title;

  @Column(length = 1000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TaskStatus status;

  @Column(name = "due_date")
  private LocalDate dueDate;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Task() {}

  public Task(
      UUID id,
      String title,
      String description,
      TaskStatus status,
      LocalDate dueDate,
      Instant createdAt) {
    this.id = id;
    this.title = title;
    this.description = description;
    this.status = status;
    this.dueDate = dueDate;
    this.createdAt = createdAt;
  }

  /** Full replace of the editable fields; id and createdAt never change. */
  public void update(String title, String description, TaskStatus status, LocalDate dueDate) {
    this.title = title;
    this.description = description;
    this.status = status;
    this.dueDate = dueDate;
  }

  public UUID getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public TaskStatus getStatus() {
    return status;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
