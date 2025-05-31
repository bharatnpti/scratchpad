package com.example.llmagentsystem.repository;

import com.example.llmagentsystem.model.entity.MessageEntity;
import org.springframework.data.domain.Pageable; // Added for pagination
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {
    // Original method, kept for other uses if any, or could be removed if not used.
    List<MessageEntity> findByConversationConversationIdOrderByTimestampDesc(UUID conversationId);

    // New method with Pageable for fetching recent messages
    List<MessageEntity> findByConversationConversationIdOrderByTimestampDesc(UUID conversationId, Pageable pageable);
}
