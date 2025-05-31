package com.example.llmagentsystem.plugin.impl;

import com.example.llmagentsystem.plugin.PluginInterface;
import com.example.llmagentsystem.plugin.PluginParameter;
import com.example.llmagentsystem.plugin.PluginSchema;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component("calculatorPlugin") // Spring bean name, matches entryPoint in PluginEntity
public class CalculatorPlugin implements PluginInterface {

    private final PluginSchema schema;

    public CalculatorPlugin() {
        this.schema = new PluginSchema(
            "calculator",
            "Performs basic arithmetic operations: add, subtract, multiply, divide.",
            List.of(
                new PluginParameter("operand1", "number", "First number", true),
                new PluginParameter("operand2", "number", "Second number", true),
                new PluginParameter("operation", "string", "Operation to perform (add, subtract, multiply, divide)", true)
            ),
            List.of(
                new PluginParameter("result", "number", "The result of the operation", true)
            )
        );
    }

    @Override
    public PluginSchema getSchema() {
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> parameters) throws IllegalArgumentException {
        Number operand1 = (Number) parameters.get("operand1");
        Number operand2 = (Number) parameters.get("operand2");
        String operation = (String) parameters.get("operation");

        if (operand1 == null || operand2 == null || operation == null) {
            throw new IllegalArgumentException("Missing required parameters for calculator plugin.");
        }

        double result;
        switch (operation.toLowerCase()) {
            case "add":
                result = operand1.doubleValue() + operand2.doubleValue();
                break;
            case "subtract":
                result = operand1.doubleValue() - operand2.doubleValue();
                break;
            case "multiply":
                result = operand1.doubleValue() * operand2.doubleValue();
                break;
            case "divide":
                if (operand2.doubleValue() == 0) {
                    throw new IllegalArgumentException("Cannot divide by zero.");
                }
                result = operand1.doubleValue() / operand2.doubleValue();
                break;
            default:
                throw new IllegalArgumentException("Invalid operation: " + operation +
                                                   ". Supported operations are add, subtract, multiply, divide.");
        }
        return Map.of("result", result);
    }
}
