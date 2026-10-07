package com.edstem.interviewprep.dto;

import com.edstem.interviewprep.enums.TaskStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Body for create (POST) and full update (PUT). A missing status defaults to {@code TODO}. */
public record TaskRequest(
    @NotBlank(message = "title is required")
        @Size(max = 100, message = "title must be at most 100 characters")
        String title,
    @Size(max = 1000, message = "description must be at most 1000 characters") String description,
    TaskStatus status,
    @FutureOrPresent(message = "dueDate cannot be in the past") LocalDate dueDate) {}
