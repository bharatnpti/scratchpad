package com.example.llmagentsystem.repository;

import com.example.llmagentsystem.model.entity.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {
    ConversationEntity findByPlatformConversationIdAndSourcePlatform(String platformConversationId, String sourcePlatform);
}
