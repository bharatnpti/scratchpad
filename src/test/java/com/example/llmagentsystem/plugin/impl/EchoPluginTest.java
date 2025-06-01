package com.example.llmagentsystem.plugin.impl;

import com.example.llmagentsystem.plugin.PluginSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EchoPluginTest {

    private EchoPlugin echoPlugin;

    @BeforeEach
    void setUp() {
        echoPlugin = new EchoPlugin();
    }

    @Test
    void getSchema_shouldReturnCorrectSchema() {
        PluginSchema schema = echoPlugin.getSchema();
        assertThat(schema).isNotNull();
        assertThat(schema.getPluginName()).isEqualTo("echo");
        assertThat(schema.getDescription()).contains("Echoes back the input message");
        assertThat(schema.getInputParameters()).hasSize(1);
        assertThat(schema.getInputParameters().get(0).getName()).isEqualTo("message");
        assertThat(schema.getOutputParameters()).hasSize(1);
        assertThat(schema.getOutputParameters().get(0).getName()).isEqualTo("echo_message");
    }

    @Test
    void execute_withValidMessage_shouldReturnEchoedMessage() {
        String testMessage = "Hello, Plugin!";
        Map<String, Object> parameters = Map.of("message", testMessage);

        Map<String, Object> result = echoPlugin.execute(parameters);

        assertThat(result).isNotNull();
        assertThat(result.get("echo_message")).isEqualTo("Echo: " + testMessage);
    }

    @Test
    void execute_missingMessageParameter_shouldThrowIllegalArgumentException() {
        Map<String, Object> parameters = Map.of(); // "message" parameter is missing

        assertThatThrownBy(() -> echoPlugin.execute(parameters))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing 'message' parameter");
    }

    @Test
    void execute_messageParameterIsNull_shouldThrowIllegalArgumentException() {
        // Note: The plugin's current implementation might treat null differently than missing.
        // If null is passed, it might try to cast null to String.
        // Let's test the defined behavior for missing. If null needs specific handling,
        // the plugin logic or parameter validation would need adjustment.
        // The current EchoPlugin checks if message == null after getting it from map.
        Map<String, Object> parameters = Map.of("message", null);
         assertThatThrownBy(() -> echoPlugin.execute(parameters))
            .isInstanceOf(IllegalArgumentException.class) // Or NullPointerException depending on internal handling before check
            .hasMessageContaining("Missing 'message' parameter"); // Based on current EchoPlugin's check
    }
}
