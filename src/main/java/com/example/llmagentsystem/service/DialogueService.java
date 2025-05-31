package com.example.llmagentsystem.service;

import com.example.llmagentsystem.model.entity.TaskEntity;
import com.example.llmagentsystem.model.nlp.StructuredNlpResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// Using the ProcessingResult from the previous stub, can be refined.
// This record is defined in CoreOrchestrationService for now, if needed here, it should be a shared model.
// For this service's own formulation logic, it might not directly need ProcessingResult if CoreOrchestrationService unpacks it.
// Let's assume formulateResponse here takes more direct parameters or a dedicated DTO.
// For now, keeping the old ProcessingResult structure for formulateResponse method signature,
// but new methods like initiateClarification are more focused.
record ProcessingResult( // This might be better as a shared DTO if used across services
    String intent,
    Map<String, Object> entities,
    Object executionOutput,
    String statusMessage,
    boolean requiresClarification
) {}

@Service
public class DialogueService {

    private static final Logger logger = LoggerFactory.getLogger(DialogueService.class);

    // In-memory store for active dialogue states. Key: conversationId
    // For production, this should be moved to a distributed cache (e.g., Redis)
    private final Map<String, DialogueState> activeDialogues = new ConcurrentHashMap<>();

    /**
     * Handles incoming user text when the conversation might be in a clarification state.
     * Attempts to use the user's text to satisfy pending clarifications.
     *
     * @param conversationId The ID of the current conversation.
     * @param currentUserInput The user's latest text input.
     * @param nlpService NlpService to re-analyze input if needed (for entity extraction from clarification).
     * @return DialogueState if clarification is ongoing or just completed, null otherwise.
     */
    public DialogueState handleClarificationInput(String conversationId, String currentUserInput, NlpService nlpService) {
        DialogueState state = activeDialogues.get(conversationId);
        if (state != null && state.isAwaitingClarification()) {
            logger.debug("Handling clarification input for conversation {}: '{}'", conversationId, currentUserInput);

            // Attempt to extract the missing entity directly from user input.
            // This is a simplified approach. A more robust way might involve targeted NLP.
            String missingEntityName = state.getMissingEntities().get(0); // Assuming we ask one by one

            // For simplicity, let's assume the user's entire input is the value for the missing entity.
            // A more advanced approach would be to use NLP to extract the specific entity value.
            // Or, if NlpService can do targeted entity extraction:
            // StructuredNlpResult clarificationNlp = nlpService.extractEntity(currentUserInput, missingEntityName, state.getPendingIntent());
            // if (clarificationNlp.entities().containsKey(missingEntityName)) {
            //    state.addClarifiedEntity(missingEntityName, clarificationNlp.entities().get(missingEntityName));
            // }

            // Simplified:
            state.addClarifiedEntity(missingEntityName, currentUserInput);
            logger.info("Added clarified entity '{}' with value '{}' for conversation {}", missingEntityName, currentUserInput, conversationId);

            if (state.isClarificationComplete()) {
                logger.info("Clarification complete for conversation {}. Pending intent: {}", conversationId, state.getPendingIntent());
                // State is returned, CoreOrchestrationService will see it's complete and re-process.
                return state;
            } else {
                // Still more entities missing, formulate next clarification question.
                return state; // Return state so caller can formulate next question
            }
        }
        return null; // Not in a clarification state or no state found
    }


    /**
     * Initiates a clarification dialogue when required entities are missing.
     *
     * @param conversationId The ID of the current conversation.
     * @param intent The intent that requires clarification.
     * @param missingEntities A list of names of entities that are missing.
     * @param originalNlpResult The original NLP result that triggered this.
     * @return A user-facing question asking for the first missing entity.
     */
    public String initiateClarification(String conversationId, String intent, List<String> missingEntities, StructuredNlpResult originalNlpResult) {
        if (missingEntities == null || missingEntities.isEmpty()) {
            logger.warn("InitiateClarification called with no missing entities for intent: {}", intent);
            return "It seems I have everything I need, but something went wrong. Could you try again?";
        }

        DialogueState state = activeDialogues.computeIfAbsent(conversationId, DialogueState::new);
        state.startClarification(intent, new ArrayList<>(missingEntities), originalNlpResult); // Use a mutable list

        logger.info("Initiating clarification for conversation {}. Intent: {}, Missing: {}", conversationId, intent, missingEntities);
        return formulateClarificationQuestion(state);
    }

    public String formulateClarificationQuestion(DialogueState state) {
        if (state == null || !state.isAwaitingClarification() || state.getMissingEntities().isEmpty()) {
            return "I think I have all I need now. Let me process that.";
        }
        String missingEntity = state.getMissingEntities().get(0); // Ask for the first one
        // Customize questions based on entity name
        switch (missingEntity.toLowerCase()) {
            case "duedate":
            case "date":
            case "time":
                return "What date or time should I set for that?";
            case "assignee":
            case "person":
                return "Who should I assign this to?";
            case "description":
            case "detail":
                return "Could you provide more details for that?";
            case "operand1":
            case "operand2":
                return "What is the " + missingEntity.replace("operand", "number ") + "?";
            case "operation":
                return "What operation would you like to perform (e.g., add, subtract)?";
            default:
                return "I need a bit more information. What about the '" + missingEntity + "'?";
        }
    }


    /**
     * Formulates a response based on the outcome of processing.
     *
     * @param processingResult The result from CoreOrchestrationService.
     * @return A string response to be sent to the user.
     */
    public String formulateResponse(ProcessingResult processingResult) {
        logger.debug("Formulating response for intent: {}, status: {}", processingResult.intent(), processingResult.statusMessage());

        if (processingResult.requiresClarification()) {
            // This path might be less used if CoreOrchestrationService calls initiateClarification directly
            return "I need a bit more information. " + processingResult.statusMessage();
        }

        if (processingResult.executionOutput() instanceof TaskEntity) {
            TaskEntity task = (TaskEntity) processingResult.executionOutput();
            return String.format("Okay, I've processed your request regarding task %s. Current status: %s. Description: %s",
                                 task.getTaskId().toString().substring(0,8),
                                 task.getStatus(),
                                 task.getDescription());
        } else if (processingResult.executionOutput() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> actionOutput = (Map<String, Object>) processingResult.executionOutput();
            return "Action '" + processingResult.intent() + "' completed. Result: " + actionOutput.toString();
        } else if (processingResult.executionOutput() instanceof String) {
            return (String) processingResult.executionOutput();
        }

        if (processingResult.statusMessage() != null && !processingResult.statusMessage().isEmpty()) {
            return processingResult.statusMessage();
        }
        return "I've processed your request.";
    }

    public DialogueState getDialogueState(String conversationId) {
        return activeDialogues.get(conversationId);
    }

    public void clearDialogueState(String conversationId) {
        DialogueState state = activeDialogues.get(conversationId);
        if (state != null) {
            state.resetClarification();
            // Optionally remove if not needed anymore: activeDialogues.remove(conversationId);
            // Or keep it for other stateful features. For now, just reset.
            logger.info("Dialogue state cleared for conversation {}", conversationId);
        }
    }


    public String directResponse(String message) {
        return message;
    }
}
