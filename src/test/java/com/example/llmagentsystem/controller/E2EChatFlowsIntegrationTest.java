package com.example.llmagentsystem.controller;

import com.example.llmagentsystem.controller.dto.ProcessTextRequest;
import com.example.llmagentsystem.model.entity.ActionEntity;
import com.example.llmagentsystem.model.entity.MessageEntity;
import com.example.llmagentsystem.model.entity.PluginEntity;
import com.example.llmagentsystem.model.entity.TaskEntity;
import com.example.llmagentsystem.model.enums.ActionStatus;
import com.example.llmagentsystem.model.enums.TaskStatus;
import com.example.llmagentsystem.model.enums.UserRole;
import com.example.llmagentsystem.repository.*; // Import all repositories
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.context.ActiveProfiles;


import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors; // Added for stream().toList()

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.reset;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class E2EChatFlowsIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired private UserRepository userRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private PluginRepository pluginRepository;
    @Autowired private ActionRepository actionRepository;


    @MockBean private ChatClient mockChatClient;
    @MockBean private VectorStore mockVectorStore;

    private final String testUserPlatformId = "e2eUser";
    private final String testConvoPlatformId = "e2eConvo";
    private final String testSourcePlatform = "e2eApiTest";

    private PluginEntity echoPluginEntity;

    @BeforeEach
    void setUp() {
        actionRepository.deleteAll();
        taskRepository.deleteAll();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();
        pluginRepository.deleteAll();

        doNothing().when(mockVectorStore).add(anyList());

        echoPluginEntity = new PluginEntity();
        echoPluginEntity.setName("echo");
        echoPluginEntity.setEntryPoint("echoPlugin");
        echoPluginEntity.setEntryPointType("spring_bean");
        echoPluginEntity.setEnabled(true);
        try {
            echoPluginEntity.setInputSchema(objectMapper.writeValueAsString(List.of(Map.of("name", "message", "type", "string"))));
            echoPluginEntity.setOutputSchema(objectMapper.writeValueAsString(List.of(Map.of("name", "echo_message", "type", "string"))));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize plugin schema for test setup", e);
        }
        pluginRepository.save(echoPluginEntity);
    }

    @AfterEach
    void tearDown() {
        actionRepository.deleteAll();
        taskRepository.deleteAll();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();
        pluginRepository.deleteAll();
        reset(mockChatClient, mockVectorStore); // Reset mocks after each test
    }

    private HttpEntity<String> createRequestEntity(Object requestDto) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(objectMapper.writeValueAsString(requestDto), headers);
    }

    @Test
    void processText_createTaskFlow_shouldCreateTaskAndStoreMessages() throws Exception {
        String inputText = "Create a task: buy groceries for the weekend";
        String llmJsonResponse = "{\"intent\": \"CREATE_TASK\", \"entities\": {\"description\": \"buy groceries for the weekend\"}}";
        ChatResponse chatResponseForUnderstanding = new ChatResponse(List.of(new Generation(llmJsonResponse)));
        when(mockChatClient.call(any(Prompt.class))).thenReturn(chatResponseForUnderstanding);

        ProcessTextRequest request = new ProcessTextRequest();
        request.setTextInput(inputText);
        request.setUserPlatformId(testUserPlatformId);
        request.setConversationPlatformId(testConvoPlatformId);
        request.setSourcePlatform(testSourcePlatform);

        ResponseEntity<String> responseEntity = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/chat/process",
                HttpMethod.POST,
                createRequestEntity(request),
                String.class
        );

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getBody()).contains("Okay, I've created task");

        List<MessageEntity> messages = messageRepository.findAll();
        assertThat(messages).hasSize(2);
        List<TaskEntity> tasks = taskRepository.findAll();
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getDescription()).isEqualTo("buy groceries for the weekend");

        verify(mockChatClient, times(1)).call(any(Prompt.class));
        verify(mockVectorStore, times(2)).add(anyList());
    }

    @Test
    void processText_executeEchoActionFlow_shouldExecuteActionAndStoreLogs() throws Exception {
        String inputText = "Echo 'Hello E2E Test'";
        String llmJsonResponse = "{\"intent\": \"ECHO\", \"entities\": {\"message\": \"Hello E2E Test\"}}";
        ChatResponse chatResponseForUnderstanding = new ChatResponse(List.of(new Generation(llmJsonResponse)));
        when(mockChatClient.call(any(Prompt.class))).thenReturn(chatResponseForUnderstanding);

        String userForEcho = testUserPlatformId + "_echo";
        String convoForEcho = testConvoPlatformId + "_echo";
        ProcessTextRequest request = new ProcessTextRequest();
        request.setTextInput(inputText);
        request.setUserPlatformId(userForEcho);
        request.setConversationPlatformId(convoForEcho);
        request.setSourcePlatform(testSourcePlatform);

        ResponseEntity<String> responseEntity = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/chat/process",
                HttpMethod.POST,
                createRequestEntity(request),
                String.class
        );

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getBody()).isEqualTo("Echo: Hello E2E Test");

        List<MessageEntity> messages = messageRepository.findAll().stream()
            .filter(m -> m.getConversation().getPlatformConversationId().equals(convoForEcho))
            .collect(Collectors.toList());
        assertThat(messages).hasSize(2);

        List<ActionEntity> actions = actionRepository.findAll();
        assertThat(actions).hasSize(1);
        ActionEntity loggedAction = actions.get(0);
        assertThat(loggedAction.getPlugin().getName()).isEqualTo("echo");
        assertThat(loggedAction.getStatus()).isEqualTo(ActionStatus.SUCCESS);

        verify(mockChatClient, times(1)).call(any(Prompt.class));
        verify(mockVectorStore, times(2)).add(anyList());
    }

    @Test
    void processText_clarificationFlow_shouldAskAndThenCreateTask() throws Exception {
        String userForClarify = testUserPlatformId + "_clarify";
        String convoForClarify = testConvoPlatformId + "_clarify";

        // 1. Initial input: "Create a task" (missing description)
        String initialInputText = "Create a new task for me";
        String llmJsonResponseInitial = "{\"intent\": \"CREATE_TASK\", \"entities\": {}}"; // No description entity
        ChatResponse chatResponseInitial = new ChatResponse(List.of(new Generation(llmJsonResponseInitial)));

        // NlpService will be called for the initial input
        when(mockChatClient.call(any(Prompt.class))).thenReturn(chatResponseInitial);

        ProcessTextRequest initialRequest = new ProcessTextRequest();
        initialRequest.setTextInput(initialInputText);
        initialRequest.setUserPlatformId(userForClarify);
        initialRequest.setConversationPlatformId(convoForClarify);
        initialRequest.setSourcePlatform(testSourcePlatform);

        // First API call - should trigger clarification
        ResponseEntity<String> responseEntityClarify = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/chat/process",
                HttpMethod.POST,
                createRequestEntity(initialRequest),
                String.class
        );

        assertThat(responseEntityClarify.getStatusCode()).isEqualTo(HttpStatus.OK);
        // DialogueService.formulateClarificationQuestion for "description"
        assertThat(responseEntityClarify.getBody()).isEqualTo("Could you provide more details for that?");

        // Verify user message and agent's clarification question stored
        List<MessageEntity> messagesAfterClarifyRequest = messageRepository.findAll().stream()
            .filter(m -> m.getConversation().getPlatformConversationId().equals(convoForClarify))
            .collect(Collectors.toList());
        assertThat(messagesAfterClarifyRequest).hasSize(2);
        assertThat(messagesAfterClarifyRequest.get(0).getContent()).isEqualTo(initialInputText);
        assertThat(messagesAfterClarifyRequest.get(1).getContent()).isEqualTo("Could you provide more details for that?");

        // Verify NlpService (via ChatClient) was called for the initial understanding
        verify(mockChatClient, times(1)).call(any(Prompt.class));
        // Verify MemoryService stored both user and agent messages
        verify(mockVectorStore, times(2)).add(anyList());


        // 2. User provides the missing description
        String clarificationInputText = "The task is to schedule a meeting for tomorrow";

        // NlpService should NOT be called again for this clarification input directly
        // as CoreOrchestrationService's DialogueState logic handles it.
        // The original NLP result (intent CREATE_TASK) is reused.
        // So, no new  specific to this input is needed for NlpService.

        ProcessTextRequest clarificationRequest = new ProcessTextRequest();
        clarificationRequest.setTextInput(clarificationInputText);
        clarificationRequest.setUserPlatformId(userForClarify);
        clarificationRequest.setConversationPlatformId(convoForClarify);
        clarificationRequest.setSourcePlatform(testSourcePlatform);

        // Second API call - providing the description
        ResponseEntity<String> responseEntityFinal = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/chat/process",
                HttpMethod.POST,
                createRequestEntity(clarificationRequest),
                String.class
        );

        assertThat(responseEntityFinal.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Now the task should be created
        assertThat(responseEntityFinal.getBody()).contains("Okay, I've created task");
        assertThat(responseEntityFinal.getBody()).contains("The task is to schedule a meeting for tomorrow");


        // 3. Verify final database state
        List<MessageEntity> messagesAfterFinalResponse = messageRepository.findAll().stream()
            .filter(m -> m.getConversation().getPlatformConversationId().equals(convoForClarify))
            .collect(Collectors.toList());
        assertThat(messagesAfterFinalResponse).hasSize(4); // Initial User, Agent Clarify, User Clarify-Input, Agent Final-Response
        assertThat(messagesAfterFinalResponse.get(2).getContent()).isEqualTo(clarificationInputText);
        assertThat(messagesAfterFinalResponse.get(3).getContent()).contains("Okay, I've created task");

        List<TaskEntity> tasks = taskRepository.findAll().stream()
            .filter(t -> t.getConversationContext() != null && t.getConversationContext().getPlatformConversationId().equals(convoForClarify))
            .collect(Collectors.toList());
        assertThat(tasks).hasSize(1);
        TaskEntity createdTask = tasks.get(0);
        assertThat(createdTask.getDescription()).isEqualTo("The task is to schedule a meeting for tomorrow");
        assertThat(createdTask.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(createdTask.getCreator().getPlatformId()).isEqualTo(userForClarify);

        // Verify NlpService (via ChatClient) was called only ONCE for the initial input.
        // Not called again for the clarification input "The task is to schedule a meeting for tomorrow"
        // because DialogueService handles clarification input directly.
        verify(mockChatClient, times(1)).call(any(Prompt.class));
        // MemoryService stores 2 more messages (user clarification + agent final response)
        verify(mockVectorStore, times(4)).add(anyList());
    }
}
