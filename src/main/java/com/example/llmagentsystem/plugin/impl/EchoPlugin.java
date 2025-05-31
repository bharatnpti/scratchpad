package com.example.llmagentsystem.plugin.impl;

import com.example.llmagentsystem.plugin.PluginInterface;
import com.example.llmagentsystem.plugin.PluginParameter;
import com.example.llmagentsystem.plugin.PluginSchema;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component("echoPlugin")
public class EchoPlugin implements PluginInterface {
    private final PluginSchema schema;

    public EchoPlugin() {
        this.schema = new PluginSchema(
            "echo",
            "Echoes back the input message.",
            List.of(
                new PluginParameter("message", "string", "The message to echo.", true)
            ),
            List.of(
                new PluginParameter("echo_message", "string", "The echoed message.", true)
            )
        );
    }

    @Override
    public PluginSchema getSchema() {
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> parameters) throws IllegalArgumentException {
        String message = (String) parameters.get("message");
        if (message == null) {
            throw new IllegalArgumentException("Missing 'message' parameter for echo plugin.");
        }
        return Map.of("echo_message", "Echo: " + message);
    }
}
