package com.edstem.interviewprep.service;

import com.edstem.interviewprep.dto.TaskRequest;
import com.edstem.interviewprep.dto.TaskResponse;
import com.edstem.interviewprep.enums.TaskStatus;
import com.edstem.interviewprep.exception.InvalidFieldException;
import com.edstem.interviewprep.exception.TaskNotFoundException;
import com.edstem.interviewprep.model.Task;
import com.edstem.interviewprep.repository.TaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

  private final TaskRepository repository;
  private final Clock clock;

  public TaskService(TaskRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional
  public TaskResponse create(TaskRequest request) {
    Task task =
        new Task(
            UUID.randomUUID(),
            request.title().strip(),
            request.description(),
            statusOrDefault(request.status()),
            request.dueDate(),
            Instant.now(clock));
    return TaskResponse.from(repository.save(task));
  }

  /** A {@code null} status lists every task. */
  @Transactional(readOnly = true)
  public List<TaskResponse> list(TaskStatus status) {
    List<Task> tasks =
        status == null
            ? repository.findAllByOrderByCreatedAtAscIdAsc()
            : repository.findAllByStatusOrderByCreatedAtAscIdAsc(status);
    return tasks.stream().map(TaskResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public TaskResponse get(UUID id) {
    return TaskResponse.from(find(id));
  }

  /** Changes to the managed entity are flushed on commit; no explicit save needed. */
  @Transactional
  public TaskResponse update(UUID id, TaskRequest request) {
    Task task = find(id);
    rejectNewPastDueDate(task, request.dueDate());
    task.update(
        request.title().strip(),
        request.description(),
        statusOrDefault(request.status()),
        request.dueDate());
    return TaskResponse.from(task);
  }

  @Transactional
  public void delete(UUID id) {
    repository.delete(find(id));
  }

  /**
   * A due date may stay in the past once it has passed (the task is overdue), but it can't be
   * changed to a date in the past.
   */
  private void rejectNewPastDueDate(Task task, LocalDate dueDate) {
    boolean changed = dueDate != null && !dueDate.equals(task.getDueDate());
    if (changed && dueDate.isBefore(LocalDate.now(clock))) {
      throw new InvalidFieldException("dueDate", "dueDate cannot be in the past");
    }
  }

  private Task find(UUID id) {
    return repository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
  }

  private static TaskStatus statusOrDefault(TaskStatus status) {
    return status == null ? TaskStatus.TODO : status;
  }
}
