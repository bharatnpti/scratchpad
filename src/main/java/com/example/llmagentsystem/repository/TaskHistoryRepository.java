package com.example.llmagentsystem.repository;

import com.example.llmagentsystem.model.entity.TaskHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;
import java.util.List;

@Repository
public interface TaskHistoryRepository extends JpaRepository<TaskHistoryEntity, UUID> {
    List<TaskHistoryEntity> findByTaskTaskIdOrderByTimestampDesc(UUID taskId);
}
