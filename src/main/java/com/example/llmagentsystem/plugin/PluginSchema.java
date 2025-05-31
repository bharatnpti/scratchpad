package com.example.llmagentsystem.plugin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PluginSchema {
    private String pluginName;
    private String description;
    private List<PluginParameter> inputParameters;
    private List<PluginParameter> outputParameters; // Or a more complex output schema
}
