package com.example.llmagentsystem.model.entity;

import com.example.llmagentsystem.model.enums.ActionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "actions")
public class ActionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "action_id", updatable = false, nullable = false)
    private UUID actionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private TaskEntity task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plugin_id", nullable = false)
    private PluginEntity plugin;

    @Column(name = "parameters", columnDefinition = "jsonb")
    @Convert(converter = JsonConverter.class) // Placeholder
    private String parameters;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ActionStatus status;

    @CreationTimestamp
    @Column(name = "start_time", updatable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time")
    private OffsetDateTime endTime;

    @Lob
    @Column(name = "execution_log")
    private String executionLog;

    @Column(name = "output", columnDefinition = "jsonb")
    @Convert(converter = JsonConverter.class) // Placeholder
    private String output;
}
