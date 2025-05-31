package com.example.llmagentsystem.repository;

import com.example.llmagentsystem.model.entity.TaskEntity;
import com.example.llmagentsystem.model.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;
import java.util.List;
import java.time.OffsetDateTime;

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {
    List<TaskEntity> findByAssigneeUserIdAndStatus(UUID assigneeUserId, TaskStatus status);
    List<TaskEntity> findByStatusAndDueDateBefore(TaskStatus status, OffsetDateTime dueDate);
}
