package com.example.llmagentsystem.service;

import com.example.llmagentsystem.model.nlp.StructuredNlpResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NlpServiceTest {

    @Mock
    private ChatClient mockChatClient;

    private NlpService nlpService;

    @BeforeEach
    void setUp() {
        nlpService = new NlpService(mockChatClient);
    }

    @Test
    void generateText_withHistory_shouldReturnContentFromChatClient() {
        String expectedResponse = "This is a mock generated response with history.";
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(expectedResponse)));

        when(mockChatClient.call(any(Prompt.class))).thenReturn(chatResponse);

        List<ChatMessageHistoryItem> history = List.of(new ChatMessageHistoryItem("User", "Previous message"));
        String actualResponse = nlpService.generateText("Test prompt", history);

        assertThat(actualResponse).isEqualTo(expectedResponse);
    }

    @Test
    void generateText_withoutHistory_shouldReturnContentFromChatClient() {
        String expectedResponse = "This is a mock generated response without history.";
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(expectedResponse)));

        when(mockChatClient.call(any(Prompt.class))).thenReturn(chatResponse);

        String actualResponse = nlpService.generateText("Test prompt", Collections.emptyList());

        assertThat(actualResponse).isEqualTo(expectedResponse);
    }

    @Test
    void understandText_withHistory_shouldUseBeanOutputParserAndReturnStructuredResult() {
        String inputText = "Yes, that's correct."; // User affirming something from history
        List<ChatMessageHistoryItem> history = List.of(
            new ChatMessageHistoryItem("Agent", "Should I create a task to buy milk for tomorrow?"),
            new ChatMessageHistoryItem("User", "Yes please") // This might be ambiguous without prior context
        );
        // LLM should be guided by prompt to use history.
        // Expected output might be an AFFIRM_INTENT or similar, or a refined CREATE_TASK from history.
        String llmMockJsonResponse = "{\\"intent\\": \\"AFFIRM_INTENT\\", \\"entities\\": {\\"confirmed_action\\": \\"CREATE_TASK_MILK\\"}}"; // Escaped JSON
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(llmMockJsonResponse)));

        when(mockChatClient.call(any(Prompt.class))).thenReturn(chatResponse);

        StructuredNlpResult result = nlpService.understandText(inputText, history);

        assertThat(result.intent()).isEqualTo("AFFIRM_INTENT");
        assertThat(result.entities()).isNotNull();
        assertThat(result.entities().get("confirmed_action")).isEqualTo("CREATE_TASK_MILK");
        assertThat(result.rawResponse()).isEqualTo(inputText);
    }

    @Test
    void understandText_withoutHistory_shouldUseBeanOutputParserAndReturnStructuredResult() {
        String inputText = "Remind me to buy eggs";
        String llmMockJsonResponse = "{\\"intent\\": \\"CREATE_TASK\\", \\"entities\\": {\\"description\\": \\"buy eggs\\"}}"; // Escaped JSON
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(llmMockJsonResponse)));

        when(mockChatClient.call(any(Prompt.class))).thenReturn(chatResponse);

        StructuredNlpResult result = nlpService.understandText(inputText, Collections.emptyList());

        assertThat(result.intent()).isEqualTo("CREATE_TASK");
        assertThat(result.entities().get("description")).isEqualTo("buy eggs");
    }


    @Test
    void generateText_whenChatClientThrowsException_shouldReturnErrorMessage() {
        when(mockChatClient.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM API error"));

        String actualResponse = nlpService.generateText("Test prompt", Collections.emptyList());

        assertThat(actualResponse).isEqualTo("Error: Could not generate text due to: LLM API error");
    }
}
