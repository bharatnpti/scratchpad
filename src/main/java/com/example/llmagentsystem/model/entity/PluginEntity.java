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
@Table(name = "plugins")
public class PluginEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "plugin_id", updatable = false, nullable = false)
    private UUID pluginId;

    @Column(name = "name", unique = true, nullable = false)
    private String name;

    @Column(name = "version")
    private String version;

    @Lob
    @Column(name = "description")
    private String description;

    @Column(name = "configuration_schema", columnDefinition = "jsonb")
    @Convert(converter = JsonConverter.class) // Placeholder
    private String configurationSchema;

    @Column(name = "input_schema", columnDefinition = "jsonb", nullable = false)
    @Convert(converter = JsonConverter.class) // Placeholder
    private String inputSchema;

    @Column(name = "output_schema", columnDefinition = "jsonb", nullable = false)
    @Convert(converter = JsonConverter.class) // Placeholder
    private String outputSchema;

    @Column(name = "entry_point", nullable = false)
    private String entryPoint; // How to call (e.g., class name, Spring bean name)

    @Column(name = "entry_point_type", nullable = false)
    private String entryPointType; // e.g., "java_class", "spring_bean"

    @Column(name = "is_enabled", nullable = false)
    private boolean isEnabled = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "plugin")
    private List<ActionEntity> actions;
}
