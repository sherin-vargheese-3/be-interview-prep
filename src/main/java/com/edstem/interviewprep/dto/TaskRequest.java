package com.edstem.interviewprep.dto;

import com.edstem.interviewprep.enums.TaskStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Body for create (POST) and full update (PUT). A missing status defaults to {@code TODO}. "Today"
 * for the due-date rule is the UTC date of the injected clock.
 */
public record TaskRequest(
    @NotBlank(message = "title is required")
        @Size(max = 100, message = "title must be at most 100 characters")
        String title,
    @Size(max = 1000, message = "description must be at most 1000 characters") String description,
    TaskStatus status,
    @FutureOrPresent(groups = TaskRequest.OnCreate.class, message = "dueDate cannot be in the past")
        LocalDate dueDate) {

  /**
   * Validation group for rules that apply only when creating. On update the past-date rule is
   * checked in the service, because it depends on the stored task: an overdue task must still be
   * editable (e.g. marked DONE) without inventing a new due date.
   */
  public interface OnCreate {}
}
