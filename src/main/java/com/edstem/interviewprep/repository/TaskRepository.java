package com.edstem.interviewprep.repository;

import com.edstem.interviewprep.enums.TaskStatus;
import com.edstem.interviewprep.model.Task;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, UUID> {

  List<Task> findAllByOrderByCreatedAtAscIdAsc();

  List<Task> findAllByStatusOrderByCreatedAtAscIdAsc(TaskStatus status);
}
