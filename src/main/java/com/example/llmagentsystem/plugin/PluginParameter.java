package com.example.llmagentsystem.plugin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PluginParameter {
    private String name;
    private String type; // e.g., "string", "number", "boolean"
    private String description;
    private boolean required;
}
