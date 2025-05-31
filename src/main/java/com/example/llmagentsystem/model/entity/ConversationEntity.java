package com.example.llmagentsystem.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "conversations")
public class ConversationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "conversation_id", updatable = false, nullable = false)
    private UUID conversationId;

    @Column(name = "platform_conversation_id", unique = true) // Similar uniqueness consideration as UserEntity.platformId
    private String platformConversationId;

    @Column(name = "source_platform")
    private String sourcePlatform;

    @Column(name = "type") // e.g., "channel", "dm", "thread"
    private String type;

    @Column(name = "last_activity_timestamp")
    private OffsetDateTime lastActivityTimestamp;

    @Lob // For potentially long text
    @Column(name = "summary")
    private String summary;

    @Column(name = "metadata", columnDefinition = "jsonb")
    @Convert(converter = JsonConverter.class) // Placeholder
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "conversation")
    private List<MessageEntity> messages;

    @OneToMany(mappedBy = "conversationContext")
    private List<TaskEntity> tasks;
}
