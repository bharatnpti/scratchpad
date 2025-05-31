package com.example.llmagentsystem.repository;

import com.example.llmagentsystem.model.entity.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {
    List<MessageEntity> findByConversationConversationIdOrderByTimestampDesc(UUID conversationId);
}
