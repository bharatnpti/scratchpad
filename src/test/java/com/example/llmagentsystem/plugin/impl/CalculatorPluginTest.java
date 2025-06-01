package com.example.llmagentsystem.plugin.impl;

import com.example.llmagentsystem.plugin.PluginSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculatorPluginTest {

    private CalculatorPlugin calculatorPlugin;

    @BeforeEach
    void setUp() {
        calculatorPlugin = new CalculatorPlugin();
    }

    @Test
    void getSchema_shouldReturnCorrectSchema() {
        PluginSchema schema = calculatorPlugin.getSchema();
        assertThat(schema).isNotNull();
        assertThat(schema.getPluginName()).isEqualTo("calculator");
        assertThat(schema.getDescription()).contains("arithmetic operations");
        assertThat(schema.getInputParameters()).hasSize(3);
        assertThat(schema.getOutputParameters()).hasSize(1);
        assertThat(schema.getOutputParameters().get(0).getName()).isEqualTo("result");
    }

    @ParameterizedTest
    @CsvSource({
        "add, 5, 3, 8.0",
        "subtract, 10, 4, 6.0",
        "multiply, 7, 6, 42.0",
        "divide, 20, 4, 5.0",
        "ADD, 5.5, 3.5, 9.0", // Test case-insensitivity for operation
        "Multiply, -2, 5, -10.0"
    })
    void execute_validOperations_shouldReturnCorrectResult(String operation, double op1, double op2, double expectedResult) {
        Map<String, Object> parameters = Map.of(
            "operand1", op1,
            "operand2", op2,
            "operation", operation
        );
        Map<String, Object> result = calculatorPlugin.execute(parameters);
        assertThat(result).isNotNull();
        assertThat(result.get("result")).isEqualTo(expectedResult);
    }

    @Test
    void execute_divideByZero_shouldThrowIllegalArgumentException() {
        Map<String, Object> parameters = Map.of(
            "operand1", 10.0,
            "operand2", 0.0,
            "operation", "divide"
        );
        assertThatThrownBy(() -> calculatorPlugin.execute(parameters))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot divide by zero");
    }

    @Test
    void execute_invalidOperation_shouldThrowIllegalArgumentException() {
        Map<String, Object> parameters = Map.of(
            "operand1", 10.0,
            "operand2", 5.0,
            "operation", "modulo" // Invalid operation
        );
        assertThatThrownBy(() -> calculatorPlugin.execute(parameters))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid operation: modulo");
    }

    @Test
    void execute_missingOperand1_shouldThrowIllegalArgumentException() {
        Map<String, Object> parameters = Map.of(
            // "operand1" is missing
            "operand2", 5.0,
            "operation", "add"
        );
        assertThatThrownBy(() -> calculatorPlugin.execute(parameters))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing required parameters");
    }

    @Test
    void execute_missingOperand2_shouldThrowIllegalArgumentException() {
        Map<String, Object> parameters = Map.of(
            "operand1", 5.0,
            // "operand2" is missing
            "operation", "add"
        );
        assertThatThrownBy(() -> calculatorPlugin.execute(parameters))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing required parameters");
    }

    @Test
    void execute_missingOperation_shouldThrowIllegalArgumentException() {
        Map<String, Object> parameters = Map.of(
            "operand1", 10.0,
            "operand2", 5.0
            // "operation" is missing
        );
        assertThatThrownBy(() -> calculatorPlugin.execute(parameters))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing required parameters");
    }
}
