package com.example.llmagentsystem.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.prompt.Prompt;

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
    void generateText_shouldReturnContentFromChatClient() {
        String expectedResponse = "This is a mock generated response.";
        // Spring AI 0.8.0 ChatResponse construction
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(expectedResponse)));

        when(mockChatClient.call(any(Prompt.class))).thenReturn(chatResponse);

        String actualResponse = nlpService.generateText("Test prompt");

        assertThat(actualResponse).isEqualTo(expectedResponse);
    }

    @Test
    void summarizeText_shouldReturnSummaryFromChatClient() {
        String textToSummarize = "This is a long text that needs summarization.";
        String expectedSummary = "Summarized text.";
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(expectedSummary)));

        when(mockChatClient.call(any(Prompt.class))).thenReturn(chatResponse);

        String actualSummary = nlpService.summarizeText(textToSummarize);

        assertThat(actualSummary).isEqualTo(expectedSummary);
    }

    @Test
    void understandText_shouldParseIntentAndEntities_simplified() {
        String inputText = "Remind me to buy milk tomorrow";
        // This mock response is what the NlpService's crude parsing logic expects
        String llmMockResponse = "Intent: CREATE_TASK, Entities: {\"item\": \"milk\", \"time\": \"tomorrow\"}";
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(llmMockResponse)));

        when(mockChatClient.call(any(Prompt.class))).thenReturn(chatResponse);

        UnderstoodText understood = nlpService.understandText(inputText);

        assertThat(understood.intent()).isEqualTo("CREATE_TASK");
        // Current crude parsing puts raw response in entities.detail
        assertThat(understood.entities()).containsKey("detail");
        assertThat(understood.entities().get("detail")).isEqualTo(llmMockResponse);
        assertThat(understood.originalText()).isEqualTo(inputText);
    }

    @Test
    void generateText_whenChatClientThrowsException_shouldReturnErrorMessage() {
        when(mockChatClient.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM API error"));

        String actualResponse = nlpService.generateText("Test prompt");

        assertThat(actualResponse).isEqualTo("Error: Could not generate text due to: LLM API error");
    }
}
