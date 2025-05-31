package com.example.llmagentsystem.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "task_history")
public class TaskHistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "history_id", updatable = false, nullable = false)
    private UUID historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private TaskEntity task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id") // User who made the change, or null if system
    private UserEntity userWhoChanged;

    @Column(name = "event_type", nullable = false)
    private String eventType; // e.g., "CREATED", "STATUS_CHANGED"

    @Column(name = "change_details", columnDefinition = "jsonb")
    @Convert(converter = JsonConverter.class) // Placeholder
    private String changeDetails;

    @CreationTimestamp
    @Column(name = "timestamp", updatable = false)
    private OffsetDateTime timestamp;
}
