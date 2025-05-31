package com.example.llmagentsystem.service;

import com.example.llmagentsystem.model.entity.TaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

// Define a simple structure for what the DialogueService might eventually receive
// to help formulate a response. This is a placeholder for now.
record ProcessingResult(
    String intent,
    Map<String, Object> entities,
    Object executionOutput, // Could be TaskEntity, action result map, or direct LLM text
    String statusMessage,
    boolean requiresClarification
) {}


@Service
public class DialogueService {

    private static final Logger logger = LoggerFactory.getLogger(DialogueService.class);

    /**
     * Generates a user-facing response based on the outcome of processing.
     * This is a stub implementation and will be significantly expanded later.
     *
     * @param result The ProcessingResult containing information about what just happened.
     * @return A string response to be sent to the user.
     */
    public String formulateResponse(ProcessingResult result) {
        logger.debug("Formulating response for intent: {}, status: {}", result.intent(), result.statusMessage());

        if (result.requiresClarification()) {
            return "I need a bit more information. " + result.statusMessage();
        }

        if (result.executionOutput() instanceof TaskEntity) {
            TaskEntity task = (TaskEntity) result.executionOutput();
            return String.format("Okay, I've processed your request regarding task %s. Current status: %s. Description: %s",
                                 task.getTaskId().toString().substring(0,8), // Short ID
                                 task.getStatus(),
                                 task.getDescription());
        } else if (result.executionOutput() instanceof Map) {
            // Assuming this is from an action execution
            @SuppressWarnings("unchecked") // Be cautious with raw type casting
            Map<String, Object> actionOutput = (Map<String, Object>) result.executionOutput();
            return "Action '" + result.intent() + "' completed. Result: " + actionOutput.toString();
        } else if (result.executionOutput() instanceof String) {
            // Direct text from LLM or a simple message
            return (String) result.executionOutput();
        }

        // Fallback based on status message if output is not specific
        if (result.statusMessage() != null && !result.statusMessage().isEmpty()) {
            return result.statusMessage();
        }

        return "I've processed your request.";
    }

    /**
     * A simpler version for direct messages when no complex processing result is available.
     * @param message The direct message to send.
     * @return The message itself.
     */
    public String directResponse(String message) {
        return message;
    }

    // Future methods:
    // - manageConversationState(...)
    // - askForClarification(String entityNeeded, String currentIntent)
    // - generateThreadedResponse(...)
}
