package com.example.llmagentsystem.service;

import com.example.llmagentsystem.model.entity.TaskEntity;
import com.example.llmagentsystem.model.nlp.StructuredNlpResult; // Assuming this is the primary NLP output
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
// NlpService.ChatMessageHistoryItem is needed if DialogueService calls NlpService.generateText
// For now, let's assume NlpService is available for direct LLM generation if needed.

// Define a more structured input for formulateResponse
enum ResponseReason {
    TASK_CREATED,
    TASK_UPDATED,
    TASK_NOT_FOUND,
    ACTION_EXECUTED,
    ACTION_FAILED,
    CLARIFICATION_NEEDED, // For the question itself
    CLARIFICATION_RECEIVED, // Info that clarification was processed
    GENERAL_NLP_RESULT, // For direct answers from LLM
    ERROR_INTERNAL,
    ERROR_NLP,
    UNHANDLED_INTENT
}

record ResponseGenerationContext(
    ResponseReason reason,
    String intent, // Original intent
    Object mainPayload, // e.g., TaskEntity, Map action result, StructuredNlpResult, error message string
    Map<String, Object> additionalInfo // e.g., original user query, entities
) {}

@Service
public class DialogueService {

    private static final Logger logger = LoggerFactory.getLogger(DialogueService.class);

    private final Map<String, DialogueState> activeDialogues = new ConcurrentHashMap<>();
    private final NlpService nlpService; // For LLM-based response generation

    @Autowired
    public DialogueService(NlpService nlpService) {
        this.nlpService = nlpService;
    }

    // --- Methods from previous step (State Management & Clarification) ---
    public DialogueState handleClarificationInput(String conversationId, String currentUserInput, NlpService nlpServiceForClarification) {
        DialogueState state = activeDialogues.get(conversationId);
        if (state != null && state.isAwaitingClarification()) {
            logger.debug("Handling clarification input for conversation {}: '{}'", conversationId, currentUserInput);
            String missingEntityName = state.getMissingEntities().get(0);
            state.addClarifiedEntity(missingEntityName, currentUserInput);
            logger.info("Added clarified entity '{}' with value '{}' for conversation {}", missingEntityName, currentUserInput, conversationId);
            return state; // Return state for CoreOrchestrationService to check if complete
        }
        return null;
    }

    public String initiateClarification(String conversationId, String intent, List<String> missingEntities, StructuredNlpResult originalNlpResult) {
        if (missingEntities == null || missingEntities.isEmpty()) {
            return "It seems I have everything I need, but something went wrong. Could you try again?";
        }
        DialogueState state = activeDialogues.computeIfAbsent(conversationId, DialogueState::new);
        state.startClarification(intent, new ArrayList<>(missingEntities), originalNlpResult);
        logger.info("Initiating clarification for conversation {}. Intent: {}, Missing: {}", conversationId, intent, missingEntities);
        return formulateClarificationQuestion(state);
    }

    public String formulateClarificationQuestion(DialogueState state) {
        if (state == null || !state.isAwaitingClarification() || state.getMissingEntities().isEmpty()) {
            return "I think I have all I need now. Let me process that.";
        }
        String missingEntity = state.getMissingEntities().get(0);
        switch (missingEntity.toLowerCase()) {
            case "duedate": case "date": case "time": return "What date or time should I set for that?";
            case "assignee": case "person": return "Who should I assign this to?";
            case "description": case "detail": return "Could you provide more details for that?";
            case "operand1": case "operand2": return "What is the " + missingEntity.replace("operand", "number ") + "?";
            case "operation": return "What operation would you like to perform (e.g., add, subtract)?";
            default: return "I need a bit more information. What about the '" + missingEntity + "'?";
        }
    }

    public DialogueState getDialogueState(String conversationId) {
        return activeDialogues.get(conversationId);
    }

    public void clearDialogueState(String conversationId) {
        DialogueState state = activeDialogues.get(conversationId);
        if (state != null) {
            state.resetClarification();
            logger.info("Dialogue state cleared for conversation {}", conversationId);
        }
    }
    // --- End of methods from previous step ---


    /**
     * Formulates a user-facing response based on the context of what happened.
     *
     * @param context The ResponseGenerationContext providing details about the event.
     * @param history Optional: conversation history for more contextual LLM responses.
     * @return A string response to be sent to the user.
     */
    public String formulateResponse(ResponseGenerationContext context, List<NlpService.ChatMessageHistoryItem> history) { // Assuming NlpService.ChatMessageHistoryItem
        logger.debug("Formulating response for reason: {}, intent: {}", context.reason(), context.intent());

        switch (context.reason()) {
            case TASK_CREATED:
                TaskEntity createdTask = (TaskEntity) context.mainPayload();
                return String.format("Okay, I've created task '%s' for you (ID: ...%s).",
                                     createdTask.getDescription().substring(0, Math.min(createdTask.getDescription().length(), 30)),
                                     createdTask.getTaskId().toString().substring(Math.max(0, createdTask.getTaskId().toString().length() - 8))); // Last 8 chars for ID
            case TASK_UPDATED:
                TaskEntity updatedTask = (TaskEntity) context.mainPayload();
                return String.format("Task '%s' (ID: ...%s) has been updated. Current status: %s.",
                                     updatedTask.getDescription().substring(0, Math.min(updatedTask.getDescription().length(), 30)),
                                     updatedTask.getTaskId().toString().substring(Math.max(0, updatedTask.getTaskId().toString().length() - 8)),
                                     updatedTask.getStatus());
            case TASK_NOT_FOUND:
                return String.format("Sorry, I couldn't find a task with the ID you provided (%s).", context.mainPayload());

            case ACTION_EXECUTED:
                @SuppressWarnings("unchecked")
                Map<String, Object> actionOutput = (Map<String, Object>) context.mainPayload();
                return String.format("Action '%s' executed successfully. Result: %s",
                                     context.intent() != null ? context.intent().toLowerCase() : "unknown action",
                                     actionOutput.toString());
            case ACTION_FAILED:
                return String.format("Sorry, action '%s' failed. Reason: %s",
                                     context.intent() != null ? context.intent().toLowerCase() : "unknown action",
                                     context.mainPayload().toString());

            case CLARIFICATION_NEEDED:
                return (String) context.mainPayload();

            case CLARIFICATION_RECEIVED:
                 return "Thanks for the information! Let me try that again.";

            case GENERAL_NLP_RESULT:
                 StructuredNlpResult nlpResult = (StructuredNlpResult) context.mainPayload();
                 String potentialAnswer = (String) nlpResult.entities().getOrDefault("answer", "");
                 if (!potentialAnswer.isEmpty()) return potentialAnswer;
                 return nlpService.generateText( // Using NlpService from DialogueService
                    "The user asked: '" + nlpResult.rawResponse() + "'. Based on your understanding (intent: " + nlpResult.intent() + "), provide a concise response.",
                    history);

            case UNHANDLED_INTENT:
                String userQuery = (String) context.additionalInfo().getOrDefault("userQuery", "your last message");
                return nlpService.generateText( // Using NlpService from DialogueService
                    "I understood your intent as '" + context.intent() + "' for the query '" + userQuery + "', but I don't have a specific way to handle that yet. Try to offer a helpful general response or ask if I can assist with something else.",
                    history);

            case ERROR_NLP:
            case ERROR_INTERNAL:
                return "I'm sorry, I encountered an error: " + context.mainPayload().toString() + ". Please try again.";

            default:
                logger.warn("Unhandled response reason: {}", context.reason());
                return "I've processed your request in a way I can't specifically describe right now.";
        }
    }

    public String directResponse(String message) {
        return message;
    }
}
