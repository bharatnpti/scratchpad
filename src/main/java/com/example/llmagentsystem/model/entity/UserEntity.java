package com.example.llmagentsystem.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "platform_id", unique = true) // Assuming unique per platform, may need composite key with sourcePlatform if not globally unique
    private String platformId;

    @Column(name = "source_platform")
    private String sourcePlatform;

    @Column(name = "username")
    private String username;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "preferences", columnDefinition = "jsonb")
    @Convert(converter = JsonConverter.class) // Placeholder for a JSON converter
    private String preferences; // Store as JSON string, or use a custom type

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "user")
    private List<MessageEntity> messages;

    @OneToMany(mappedBy = "creator")
    private List<TaskEntity> createdTasks;

    @OneToMany(mappedBy = "assignee")
    private List<TaskEntity> assignedTasks;

    @OneToMany(mappedBy = "userWhoChanged")
    private List<TaskHistoryEntity> taskHistoryEntries;
}
