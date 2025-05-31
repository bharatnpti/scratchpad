package com.example.llmagentsystem.model.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
// import com.fasterxml.jackson.databind.ObjectMapper; // Example for Jackson

@Converter
public class JsonConverter implements AttributeConverter<String, String> {

    // private final static ObjectMapper objectMapper = new ObjectMapper(); // Example

    @Override
    public String convertToDatabaseColumn(String attribute) {
        // Implement serialization if 'attribute' is an object
        // For now, assuming it's already a JSON string or handled by jsonb type
        return attribute;
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        // Implement deserialization if 'dbData' needs to be an object
        // For now, returning as string
        return dbData;
    }
}
