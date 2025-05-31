package com.example.llmagentsystem.model.entity;

import com.example.llmagentsystem.model.enums.UserRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "messages")
public class MessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "message_id", updatable = false, nullable = false)
    private UUID messageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private ConversationEntity conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(name = "platform_message_id")
    private String platformMessageId;

    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "embedding_id") // Reference to vector in Vector DB
    private String embeddingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role;

    @Column(name = "metadata", columnDefinition = "jsonb")
    @Convert(converter = JsonConverter.class) // Placeholder
    private String metadata;

    @CreationTimestamp
    @Column(name = "timestamp", updatable = false)
    private OffsetDateTime timestamp;
}
