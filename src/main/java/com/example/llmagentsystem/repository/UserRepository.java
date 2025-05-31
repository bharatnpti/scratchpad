package com.example.llmagentsystem.repository;

import com.example.llmagentsystem.model.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    // Custom query methods can be added here
    UserEntity findByPlatformIdAndSourcePlatform(String platformId, String sourcePlatform);
    UserEntity findByEmail(String email);
}
