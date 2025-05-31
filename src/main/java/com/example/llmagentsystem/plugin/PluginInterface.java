package com.example.llmagentsystem.plugin;

import java.util.Map;

public interface PluginInterface {
    /**
     * Gets the schema defining the plugin's name, description, inputs, and outputs.
     * This helps in understanding how to use the plugin and validating parameters.
     * @return PluginSchema describing the plugin.
     */
    PluginSchema getSchema();

    /**
     * Executes the plugin with the given parameters.
     * @param parameters A map of parameter names to values, conforming to the inputSchema.
     * @return A map of output names to values, or a more structured object.
     * @throws IllegalArgumentException if parameters are invalid.
     */
    Map<String, Object> execute(Map<String, Object> parameters) throws IllegalArgumentException;
}
