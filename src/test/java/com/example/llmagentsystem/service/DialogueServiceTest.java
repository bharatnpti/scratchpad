package com.example.llmagentsystem.service;

import com.example.llmagentsystem.model.entity.TaskEntity;
import com.example.llmagentsystem.model.nlp.StructuredNlpResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DialogueServiceTest {

    @Mock
    private NlpService mockNlpService; // Mock NlpService for LLM-based response generation

    private DialogueService dialogueService;

    private final String conversationId = "conv123";

    @BeforeEach
    void setUp() {
        dialogueService = new DialogueService(mockNlpService);
        // Clear active dialogues for each test if DialogueService instance is reused
        // (it's new for each test here due to new DialogueService(mockNlpService))
    }

    @Test
    void initiateClarification_shouldCreateDialogueStateAndReturnQuestion() {
        String intent = "CREATE_TASK";
        List<String> missingEntities = List.of("description", "dueDate");
        StructuredNlpResult originalNlpResult = new StructuredNlpResult(intent, Collections.emptyMap(), "create a task");

        String question = dialogueService.initiateClarification(conversationId, intent, missingEntities, originalNlpResult);

        assertThat(question).isEqualTo("Could you provide more details for that?"); // As 'description' is first
        DialogueState state = dialogueService.getDialogueState(conversationId);
        assertThat(state).isNotNull();
        assertThat(state.isAwaitingClarification()).isTrue();
        assertThat(state.getPendingIntent()).isEqualTo(intent);
        assertThat(state.getMissingEntities()).containsExactly("description", "dueDate");
        assertThat(state.getOriginalNlpResult()).isEqualTo(originalNlpResult);
    }

    @Test
    void handleClarificationInput_whenAwaitingClarification_shouldUpdateState() {
        // Setup initial state
        String intent = "CREATE_TASK";
        List<String> missingEntities = new ArrayList<>(List.of("description", "dueDate")); // Mutable list
        StructuredNlpResult originalNlpResult = new StructuredNlpResult(intent, Collections.emptyMap(), "create task");
        dialogueService.initiateClarification(conversationId, intent, missingEntities, originalNlpResult);

        String userInput = "It's for buying groceries";
        DialogueState updatedState = dialogueService.handleClarificationInput(conversationId, userInput, mockNlpService);

        assertThat(updatedState).isNotNull();
        assertThat(updatedState.getCollectedEntities()).containsEntry("description", userInput);
        assertThat(updatedState.getMissingEntities()).containsExactly("dueDate"); // 'description' removed
        assertThat(updatedState.isAwaitingClarification()).isTrue(); // Still awaiting 'dueDate'
    }

    @Test
    void handleClarificationInput_whenClarificationCompletes_shouldMarkAsComplete() {
        String intent = "CREATE_TASK";
        List<String> missingEntities = new ArrayList<>(List.of("description")); // Only one missing
        StructuredNlpResult originalNlpResult = new StructuredNlpResult(intent, Collections.emptyMap(), "create task");
        dialogueService.initiateClarification(conversationId, intent, missingEntities, originalNlpResult);

        String userInput = "Buy milk";
        DialogueState finalState = dialogueService.handleClarificationInput(conversationId, userInput, mockNlpService);

        assertThat(finalState.isClarificationComplete()).isTrue();
        assertThat(finalState.getCollectedEntities()).containsEntry("description", "Buy milk");
        assertThat(finalState.getMissingEntities()).isEmpty();
    }

    @Test
    void handleClarificationInput_whenNotAwaitingClarification_shouldReturnNull() {
        DialogueState state = dialogueService.handleClarificationInput(conversationId, "some input", mockNlpService);
        assertThat(state).isNull();
    }

    @Test
    void formulateClarificationQuestion_shouldReturnCorrectQuestionForEntity() {
        DialogueState state = new DialogueState(conversationId);
        state.setAwaitingClarification(true);

        state.setMissingEntities(new ArrayList<>(List.of("dueDate")));
        assertThat(dialogueService.formulateClarificationQuestion(state)).isEqualTo("What date or time should I set for that?");

        state.setMissingEntities(new ArrayList<>(List.of("assignee")));
        assertThat(dialogueService.formulateClarificationQuestion(state)).isEqualTo("Who should I assign this to?");

        state.setMissingEntities(new ArrayList<>(List.of("unknownEntity")));
        assertThat(dialogueService.formulateClarificationQuestion(state)).isEqualTo("I need a bit more information. What about the 'unknownEntity'?");
    }

    @Test
    void formulateClarificationQuestion_whenClarificationNotNeeded_shouldReturnDefaultMessage() {
        DialogueState state = new DialogueState(conversationId); // Not awaiting, or missingEntities is empty
        assertThat(dialogueService.formulateClarificationQuestion(state)).isEqualTo("I think I have all I need now. Let me process that.");

        state.setAwaitingClarification(true);
        state.setMissingEntities(Collections.emptyList());
        assertThat(dialogueService.formulateClarificationQuestion(state)).isEqualTo("I think I have all I need now. Let me process that.");
    }


    @Test
    void formulateResponse_forTaskCreated_shouldFormatTaskCreationMessage() {
        TaskEntity task = new TaskEntity();
        task.setTaskId(UUID.randomUUID());
        task.setDescription("A very long description for the new task");
        ResponseGenerationContext context = new ResponseGenerationContext(
            ResponseReason.TASK_CREATED, "CREATE_TASK", task, Collections.emptyMap()
        );
        String response = dialogueService.formulateResponse(context, Collections.emptyList());
        assertThat(response).contains("Okay, I've created task 'A very long description for t...' for you (ID: ..." + task.getTaskId().toString().substring(Math.max(0, task.getTaskId().toString().length() - 8)) + ").");
    }

    @Test
    void formulateResponse_forActionExecuted_shouldFormatActionResponseMessage() {
        Map<String, Object> actionResult = Map.of("detail", "Calculator result is 42");
        ResponseGenerationContext context = new ResponseGenerationContext(
            ResponseReason.ACTION_EXECUTED, "CALCULATOR", actionResult, Collections.emptyMap()
        );
        String response = dialogueService.formulateResponse(context, Collections.emptyList());
        assertThat(response).isEqualTo("Action 'calculator' executed successfully. Result: {detail=Calculator result is 42}");
    }

    @Test
    void formulateResponse_forUnhandledIntent_shouldCallNlpServiceForGeneration() {
        String userQuery = "Tell me a joke";
        StructuredNlpResult nlpResult = new StructuredNlpResult("TELL_JOKE", Collections.emptyMap(), userQuery);
        ResponseGenerationContext context = new ResponseGenerationContext(
            ResponseReason.UNHANDLED_INTENT, "TELL_JOKE", nlpResult, Map.of("userQuery", userQuery)
        );

        String expectedGeneratedResponse = "Why did the chicken cross the road? To get to the other side!";
        when(mockNlpService.generateText(anyString(), anyList())).thenReturn(expectedGeneratedResponse);

        String response = dialogueService.formulateResponse(context, Collections.emptyList());

        assertThat(response).isEqualTo(expectedGeneratedResponse);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockNlpService).generateText(promptCaptor.capture(), anyList());
        assertThat(promptCaptor.getValue()).contains("I understood your intent as 'TELL_JOKE' for the query '" + userQuery + "'");
    }

    @Test
    void clearDialogueState_shouldResetState() {
        // Setup a state
        dialogueService.initiateClarification(conversationId, "TEST_INTENT", List.of("test_entity"), null);
        assertThat(dialogueService.getDialogueState(conversationId)).isNotNull();
        assertThat(dialogueService.getDialogueState(conversationId).isAwaitingClarification()).isTrue();

        dialogueService.clearDialogueState(conversationId);

        DialogueState clearedState = dialogueService.getDialogueState(conversationId);
        // In current implementation, clearDialogueState only resets fields, doesn't remove from map.
        // So state is not null, but isAwaitingClarification is false.
        assertThat(clearedState).isNotNull();
        assertThat(clearedState.isAwaitingClarification()).isFalse();
        assertThat(clearedState.getPendingIntent()).isNull();
    }

}
