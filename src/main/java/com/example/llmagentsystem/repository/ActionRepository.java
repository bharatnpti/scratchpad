package com.example.llmagentsystem.repository;

import com.example.llmagentsystem.model.entity.ActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;
import java.util.List;

@Repository
public interface ActionRepository extends JpaRepository<ActionEntity, UUID> {
    List<ActionEntity> findByTaskTaskId(UUID taskId);
}
