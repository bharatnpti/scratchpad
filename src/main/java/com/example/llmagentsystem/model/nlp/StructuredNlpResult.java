package com.example.llmagentsystem.model.nlp;

import java.util.Map;
import java.util.List;

// Using records for immutability and conciseness
public record StructuredNlpResult(
    String intent,
    Map<String, Object> entities, // Can be nested maps or specific entity objects later
    String rawResponse, // The raw text from which intent/entities were derived (optional)
    Double confidence // Optional: if the model or parsing provides a confidence score
) {
    // Constructor for when confidence is not available
    public StructuredNlpResult(String intent, Map<String, Object> entities, String rawResponse) {
        this(intent, entities, rawResponse, null);
    }
}
