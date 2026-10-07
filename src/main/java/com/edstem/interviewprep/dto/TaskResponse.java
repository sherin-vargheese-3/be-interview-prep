package com.edstem.interviewprep.dto;

import com.edstem.interviewprep.enums.TaskStatus;
import com.edstem.interviewprep.model.Task;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TaskResponse(
    UUID id,
    String title,
    String description,
    TaskStatus status,
    LocalDate dueDate,
    Instant createdAt) {

  public static TaskResponse from(Task task) {
    return new TaskResponse(
        task.getId(),
        task.getTitle(),
        task.getDescription(),
        task.getStatus(),
        task.getDueDate(),
        task.getCreatedAt());
  }
}
