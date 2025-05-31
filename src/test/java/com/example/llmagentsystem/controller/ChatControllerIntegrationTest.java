package com.example.llmagentsystem.controller;

import com.example.llmagentsystem.controller.dto.ProcessTextRequest;
import com.example.llmagentsystem.service.CoreOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class) // Test only the ChatController layer
class ChatControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean // Use MockBean to mock the service dependency
    private CoreOrchestrationService mockCoreOrchestrationService;

    @Test
    void processText_whenValidRequest_shouldReturnSuccess() throws Exception {
        ProcessTextRequest request = new ProcessTextRequest();
        request.setTextInput("Hello agent");
        request.setUserPlatformId("testUser123");
        request.setConversationPlatformId("testConvo456");
        request.setSourcePlatform("api_test");

        String expectedAgentResponse = "Hello user, I processed your message: Hello agent";
        when(mockCoreOrchestrationService.processIncomingText(
                request.getTextInput(),
                request.getUserPlatformId(),
                request.getConversationPlatformId(),
                request.getSourcePlatform()
        )).thenReturn(expectedAgentResponse);

        mockMvc.perform(post("/api/v1/chat/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseText").value(expectedAgentResponse))
                .andExpect(jsonPath("$.userPlatformId").value("testUser123"))
                .andExpect(jsonPath("$.conversationPlatformId").value("testConvo456"));
    }

    @Test
    void processText_whenTextInputIsBlank_shouldReturnBadRequest() throws Exception {
        ProcessTextRequest request = new ProcessTextRequest();
        request.setTextInput(""); // Blank input
        request.setUserPlatformId("testUser123");
        request.setConversationPlatformId("testConvo456");
        request.setSourcePlatform("api_test");

        mockMvc.perform(post("/api/v1/chat/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
                // Potentially also check for error message in response if controller provides one for validation failures
    }

    @Test
    void processText_whenServiceThrowsIllegalArgumentException_shouldReturnBadRequestWithMessage() throws Exception {
        ProcessTextRequest request = new ProcessTextRequest();
        request.setTextInput("Valid input");
        request.setUserPlatformId("testUserError");
        request.setConversationPlatformId("testConvoError");
        request.setSourcePlatform("api_test_error");

        String errorMessage = "Invalid arguments for processing";
        when(mockCoreOrchestrationService.processIncomingText(anyString(), anyString(), anyString(), anyString()))
            .thenThrow(new IllegalArgumentException(errorMessage));

        mockMvc.perform(post("/api/v1/chat/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.responseText").value("Error: " + errorMessage));
    }

    @Test
    void processText_whenServiceThrowsGenericException_shouldReturnInternalServerError() throws Exception {
        ProcessTextRequest request = new ProcessTextRequest();
        request.setTextInput("Cause generic error");
        request.setUserPlatformId("testUserGenericError");
        request.setConversationPlatformId("testConvoGenericError");
        request.setSourcePlatform("api_test_generic_error");

        when(mockCoreOrchestrationService.processIncomingText(anyString(), anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("Some internal service error"));

        mockMvc.perform(post("/api/v1/chat/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.responseText").value("An internal error occurred."));
    }
}
