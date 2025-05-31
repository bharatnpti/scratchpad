package com.example.llmagentsystem.controller;

import com.example.llmagentsystem.controller.dto.AgentResponse;
import com.example.llmagentsystem.controller.dto.ProcessTextRequest;
import com.example.llmagentsystem.service.CoreOrchestrationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Chat API", description = "Endpoints for interacting with the LLM Agent")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final CoreOrchestrationService coreOrchestrationService;

    @Autowired
    public ChatController(CoreOrchestrationService coreOrchestrationService) {
        this.coreOrchestrationService = coreOrchestrationService;
    }

    @PostMapping("/process")
    @Operation(summary = "Process a text input from a user",
               description = "Receives text from a user, processes it through the agent's core logic, and returns the agent's response.")
    @ApiResponse(responseCode = "200", description = "Successful processing, agent response returned")
    @ApiResponse(responseCode = "400", description = "Invalid request payload")
    @ApiResponse(responseCode = "500", description = "Internal server error during processing")
    public ResponseEntity<AgentResponse> processText(@Valid @RequestBody ProcessTextRequest request) {
        logger.info("Received API request to process text for user {} on platform {}",
                    request.getUserPlatformId(), request.getSourcePlatform());
        try {
            String responseText = coreOrchestrationService.processIncomingText(
                    request.getTextInput(),
                    request.getUserPlatformId(),
                    request.getConversationPlatformId(),
                    request.getSourcePlatform()
            );
            AgentResponse agentResponse = new AgentResponse(
                    responseText,
                    request.getUserPlatformId(),
                    request.getConversationPlatformId()
            );
            return ResponseEntity.ok(agentResponse);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request processing text: {}", e.getMessage());
            // Consider a more structured error response
            return ResponseEntity.badRequest().body(new AgentResponse("Error: " + e.getMessage(), request.getUserPlatformId(), request.getConversationPlatformId()));
        } catch (Exception e) {
            logger.error("Error processing text for user {}: {}", request.getUserPlatformId(), e.getMessage(), e);
            // Avoid sending internal error details to the client in production
            return ResponseEntity.status(500).body(new AgentResponse("An internal error occurred.", request.getUserPlatformId(), request.getConversationPlatformId()));
        }
    }
}
