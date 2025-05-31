package com.example.llmagentsystem.repository;

import com.example.llmagentsystem.model.entity.PluginEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface PluginRepository extends JpaRepository<PluginEntity, UUID> {
    Optional<PluginEntity> findByName(String name);
}
