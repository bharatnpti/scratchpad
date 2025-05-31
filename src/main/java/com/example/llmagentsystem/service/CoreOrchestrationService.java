package com.example.llmagentsystem.service;

import com.example.llmagentsystem.model.entity.ConversationEntity;
import com.example.llmagentsystem.model.entity.MessageEntity;
import com.example.llmagentsystem.model.entity.UserEntity;
import com.example.llmagentsystem.model.enums.UserRole;
import com.example.llmagentsystem.repository.ConversationRepository;
import com.example.llmagentsystem.repository.MessageRepository;
import com.example.llmagentsystem.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class CoreOrchestrationService {

    private static final Logger logger = LoggerFactory.getLogger(CoreOrchestrationService.class);

    private final NlpService nlpService;
    private final MemoryService memoryService;
    private final TaskService taskService;
    private final ActionExecutorService actionExecutorService;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    // DialogueService will be integrated later

    @Autowired
    public CoreOrchestrationService(
            NlpService nlpService,
            MemoryService memoryService,
            TaskService taskService,
            ActionExecutorService actionExecutorService,
            UserRepository userRepository,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository) {
        this.nlpService = nlpService;
        this.memoryService = memoryService;
        this.taskService = taskService;
        this.actionExecutorService = actionExecutorService;
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public String processIncomingText(String textInput, String userPlatformId, String conversationPlatformId, String sourcePlatform) {
        logger.info("Processing text input: '{}' from user {} in conversation {} on platform {}",
                textInput, userPlatformId, conversationPlatformId, sourcePlatform);

        // 1. Get or Create User and Conversation Entities
        UserEntity user = getOrCreateUser(userPlatformId, sourcePlatform);
        ConversationEntity conversation = getOrCreateConversation(conversationPlatformId, sourcePlatform, user);
        conversation.setLastActivityTimestamp(OffsetDateTime.now());
        conversationRepository.save(conversation);


        // 2. Store User Message
        MessageEntity userMessage = new MessageEntity();
        userMessage.setContent(textInput);
        userMessage.setConversation(conversation);
        userMessage.setUser(user);
        userMessage.setRole(UserRole.USER);
        userMessage.setTimestamp(OffsetDateTime.now()); // Ensure timestamp is set before saving
        // PlatformMessageID would typically come from the adapter
        userMessage.setPlatformMessageId("simulated_platform_msg_id_" + UUID.randomUUID());
        MessageEntity savedUserMessage = messageRepository.save(userMessage);
        memoryService.storeMessageMemory(savedUserMessage); // Store in vector DB

        // 3. Retrieve Relevant Context (Simplified for now)
        // List<RetrievedContextItem> contextItems = memoryService.retrieveRelevantContext(textInput, conversation.getConversationId(), 5);
        // String contextForPrompt = contextItems.stream().map(RetrievedContextItem::content).collect(Collectors.joining("\n"));
        // logger.debug("Retrieved context for prompt: {}", contextForPrompt);
        // For simplicity, not passing explicit context to NLP for now, but MemoryService is ready.

        // 4. Understand Text using NlpService
        UnderstoodText understoodText = nlpService.understandText(textInput);
        logger.debug("Understood text - Intent: {}, Entities: {}", understoodText.intent(), understoodText.entities());

        String agentResponseText = "I'm not sure how to respond to that."; // Default response

        // 5. Route based on Intent
        try {
            switch (understoodText.intent().toUpperCase()) {
                case "CREATE_TASK":
                    // Extract task details from entities (highly simplified)
                    String description = (String) understoodText.entities().getOrDefault("detail", "Task from: " + textInput);
                    // UserEntity assignee = null; // TODO: Extract assignee from entities if present
                    // OffsetDateTime dueDate = null; // TODO: Extract due date
                    taskService.createTask(description, user, null, null, null, conversation, null);
                    agentResponseText = "Okay, I've created a task for you: " + description;
                    break;

                case "GET_WEATHER": // Example, assumes a "weather" plugin exists
                case "CALCULATOR": // Example, assumes "calculator" plugin exists
                    // This assumes the intent directly maps to a plugin name or can be derived.
                    String pluginName = understoodText.intent().toLowerCase();
                    Map<String, Object> params = new HashMap<>(understoodText.entities());
                    // Crude parameter mapping for calculator
                    if ("CALCULATOR".equalsIgnoreCase(understoodText.intent())) {
                        // A more robust solution would parse entities properly.
                        // This is just a placeholder to make it somewhat runnable for calculator.
                        // E.g. "calculate 2 plus 3" -> entities: {"operand1": 2, "operand2": 3, "operation": "add"}
                        // For now, we'll assume entities are already structured if this intent is hit.
                        // Example: if entities map is {"operand1": "2", "operand2": "5", "operation": "add"}
                        // Convert String values to Number if needed by the plugin
                        params.computeIfPresent("operand1", (k, v) -> Double.parseDouble(v.toString()));
                        params.computeIfPresent("operand2", (k, v) -> Double.parseDouble(v.toString()));
                    }

                    Map<String, Object> actionResult = actionExecutorService.executeAction(pluginName, params, null);
                    agentResponseText = "Action '" + pluginName + "' executed. Result: " + actionResult.toString();
                    break;

                case "ECHO": // Example for echo plugin
                     Map<String, Object> echoParams = Map.of("message", textInput);
                     Map<String, Object> echoResult = actionExecutorService.executeAction("echo", echoParams, null);
                     agentResponseText = echoResult.getOrDefault("echo_message", "Echo failed.").toString();
                     break;

                case "INTENT_UNCLEAR":
                case "ERROR_UNDERSTANDING":
                    agentResponseText = "I'm having a bit of trouble understanding that. Could you try rephrasing?";
                    break;

                default:
                    // If no specific intent matched, try a generic generation
                    logger.info("No specific intent matched, attempting generic text generation for: {}", textInput);
                    // agentResponseText = nlpService.generateText("Respond to the user's message: " + textInput);
                    // For now, keep it simple:
                    agentResponseText = "Thanks for your message: '" + textInput + "'. I'm still learning how to respond to this.";
                    break;
            }
        } catch (Exception e) {
            logger.error("Error processing intent {}: {}", understoodText.intent(), e.getMessage(), e);
            agentResponseText = "I encountered an error trying to process your request. Please try again.";
        }

        // 6. Store Agent Response
        MessageEntity agentMessage = new MessageEntity();
        agentMessage.setContent(agentResponseText);
        agentMessage.setConversation(conversation);
        agentMessage.setUser(getAgentUser()); // Agent is also a user
        agentMessage.setRole(UserRole.AGENT);
        agentMessage.setTimestamp(OffsetDateTime.now());
        MessageEntity savedAgentMessage = messageRepository.save(agentMessage);
        memoryService.storeMessageMemory(savedAgentMessage);

        return agentResponseText;
    }

    private UserEntity getOrCreateUser(String platformId, String sourcePlatform) {
        return userRepository.findByPlatformIdAndSourcePlatform(platformId, sourcePlatform)
                .orElseGet(() -> {
                    UserEntity newUser = new UserEntity();
                    newUser.setPlatformId(platformId);
                    newUser.setSourcePlatform(sourcePlatform);
                    newUser.setDisplayName("User " + platformId); // Placeholder
                    logger.info("Creating new user for platformId: {} on platform: {}", platformId, sourcePlatform);
                    return userRepository.save(newUser);
                });
    }

    private ConversationEntity getOrCreateConversation(String platformId, String sourcePlatform, UserEntity user) {
         // For simplicity, using platformId also for conversation. A real system might have distinct IDs.
        return conversationRepository.findByPlatformConversationIdAndSourcePlatform(platformId, sourcePlatform)
                .orElseGet(() -> {
                    ConversationEntity newConversation = new ConversationEntity();
                    newConversation.setPlatformConversationId(platformId);
                    newConversation.setSourcePlatform(sourcePlatform);
                    newConversation.setType("direct_message"); // Placeholder type
                    // newConversation.setLastActivityTimestamp(OffsetDateTime.now()); // Set by caller
                    logger.info("Creating new conversation for platformId: {} on platform: {}", platformId, sourcePlatform);
                    return conversationRepository.save(newConversation);
                });
    }

    private UserEntity getAgentUser() {
        // Represents the agent itself as a user in the system for logging its messages.
        // This user should be pre-provisioned or created on startup.
        final String AGENT_PLATFORM_ID = "SYSTEM_AGENT";
        final String AGENT_SOURCE_PLATFORM = "INTERNAL";

        return userRepository.findByPlatformIdAndSourcePlatform(AGENT_PLATFORM_ID, AGENT_SOURCE_PLATFORM)
            .orElseGet(() -> {
                UserEntity agentUser = new UserEntity();
                agentUser.setPlatformId(AGENT_PLATFORM_ID);
                agentUser.setSourcePlatform(AGENT_SOURCE_PLATFORM);
                agentUser.setDisplayName("AI Agent");
                agentUser.setEmail("agent@system.internal");
                logger.info("Creating system agent user.");
                return userRepository.save(agentUser);
            });
    }
}
