package com.example.llmagentsystem.service;

import com.example.llmagentsystem.model.entity.ConversationEntity;
import com.example.llmagentsystem.model.entity.MessageEntity;
import com.example.llmagentsystem.model.entity.TaskEntity;
import com.example.llmagentsystem.model.entity.UserEntity;
import com.example.llmagentsystem.model.enums.UserRole;
import com.example.llmagentsystem.model.nlp.StructuredNlpResult;
import com.example.llmagentsystem.repository.ConversationRepository;
import com.example.llmagentsystem.repository.MessageRepository;
import com.example.llmagentsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;


import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoreOrchestrationServiceTest {

    @Mock private NlpService mockNlpService;
    @Mock private MemoryService mockMemoryService;
    @Mock private TaskService mockTaskService;
    @Mock private ActionExecutorService mockActionExecutorService;
    @Mock private UserRepository mockUserRepository;
    @Mock private ConversationRepository mockConversationRepository;
    @Mock private MessageRepository mockMessageRepository;
    @Mock private DialogueService mockDialogueService; // Added from recent plan steps

    @InjectMocks
    private CoreOrchestrationService coreOrchestrationService;

    private UserEntity testUser;
    private UserEntity agentUser;
    private ConversationEntity testConversation;
    private final String userPlatformId = "user123";
    private final String convoPlatformId = "convo456";
    private final String sourcePlatform = "test_platform";

    @BeforeEach
    void setUp() {
        testUser = new UserEntity();
        testUser.setUserId(UUID.randomUUID());
        testUser.setPlatformId(userPlatformId);
        testUser.setSourcePlatform(sourcePlatform);
        testUser.setDisplayName("Test User");

        agentUser = new UserEntity(); // For agent's own messages
        agentUser.setUserId(UUID.randomUUID());
        agentUser.setPlatformId("SYSTEM_AGENT");
        agentUser.setSourcePlatform("INTERNAL");
        agentUser.setDisplayName("AI Agent");


        testConversation = new ConversationEntity();
        testConversation.setConversationId(UUID.randomUUID());
        testConversation.setPlatformConversationId(convoPlatformId);
        testConversation.setSourcePlatform(sourcePlatform);

        // Mock repository calls for getOrCreateUser/Conversation and agent user
        when(mockUserRepository.findByPlatformIdAndSourcePlatform(userPlatformId, sourcePlatform))
            .thenReturn(Optional.of(testUser));
        when(mockConversationRepository.findByPlatformConversationIdAndSourcePlatform(convoPlatformId, sourcePlatform))
            .thenReturn(Optional.of(testConversation));
        when(mockUserRepository.findByPlatformIdAndSourcePlatform("SYSTEM_AGENT", "INTERNAL"))
            .thenReturn(Optional.of(agentUser));

        // Mock message saving
        when(mockMessageRepository.save(any(MessageEntity.class))).thenAnswer(invocation -> {
            MessageEntity msg = invocation.getArgument(0);
            if (msg.getMessageId() == null) msg.setMessageId(UUID.randomUUID());
            return msg;
        });

        // Mock DialogueService basic behavior
        when(mockDialogueService.getDialogueState(anyString())).thenReturn(null); // No active clarification by default

        // Mock chat history retrieval
        when(mockMessageRepository.findByConversationConversationIdOrderByTimestampDesc(any(UUID.class), any(Pageable.class)))
            .thenReturn(Collections.emptyList());
    }

    @Test
    void processIncomingText_shouldStoreUserMessageAndCallNlpService() {
        String inputText = "Hello there";
        StructuredNlpResult nlpResult = new StructuredNlpResult("GREETING", Collections.emptyMap(), inputText);
        when(mockNlpService.understandText(eq(inputText), anyList())).thenReturn(nlpResult);

        when(mockDialogueService.formulateResponse(any(ResponseGenerationContext.class), anyList()))
            .thenReturn("Hello to you too!");


        coreOrchestrationService.processIncomingText(inputText, userPlatformId, convoPlatformId, sourcePlatform);

        verify(mockMessageRepository, times(2)).save(any(MessageEntity.class));
        verify(mockMemoryService, times(2)).storeMessageMemory(any(MessageEntity.class));
        verify(mockNlpService).understandText(eq(inputText), anyList());
    }

    @Test
    void processIncomingText_whenIntentIsCreateTask_shouldCallTaskService() {
        String inputText = "Create a task to buy milk";
        Map<String, Object> entities = Map.of("description", "buy milk");
        StructuredNlpResult nlpResult = new StructuredNlpResult("CREATE_TASK", entities, inputText);

        when(mockNlpService.understandText(eq(inputText), anyList())).thenReturn(nlpResult);

        TaskEntity createdTask = new TaskEntity();
        createdTask.setTaskId(UUID.randomUUID());
        createdTask.setDescription("buy milk");
        // Ensure all parameters match, using any() for those not strictly controlled in this test focus
        when(mockTaskService.createTask(eq("buy milk"), eq(testUser), isNull(), isNull(), isNull(), eq(testConversation), isNull() ))
            .thenReturn(createdTask);

        ArgumentCaptor<ResponseGenerationContext> rgcCaptor = ArgumentCaptor.forClass(ResponseGenerationContext.class);
        when(mockDialogueService.formulateResponse(rgcCaptor.capture(), anyList()))
            .thenReturn("Task created: buy milk");


        coreOrchestrationService.processIncomingText(inputText, userPlatformId, convoPlatformId, sourcePlatform);

        verify(mockTaskService).createTask(eq("buy milk"), eq(testUser), isNull(), isNull(), isNull(), eq(testConversation), isNull());
        assertThat(rgcCaptor.getValue().reason()).isEqualTo(ResponseReason.TASK_CREATED);
        assertThat(rgcCaptor.getValue().mainPayload()).isEqualTo(createdTask);
    }

    @Test
    void processIncomingText_whenIntentIsEchoAction_shouldCallActionExecutorService() {
        String inputText = "Echo this message";
        Map<String, Object> entities = Map.of("message", inputText);
        StructuredNlpResult nlpResult = new StructuredNlpResult("ECHO", entities, inputText);
        Map<String, Object> actionResult = Map.of("echo_message", "Echo: " + inputText);

        when(mockNlpService.understandText(eq(inputText), anyList())).thenReturn(nlpResult);
        when(mockActionExecutorService.executeAction(eq("echo"), eq(entities), isNull())).thenReturn(actionResult);

        ArgumentCaptor<ResponseGenerationContext> rgcCaptor = ArgumentCaptor.forClass(ResponseGenerationContext.class);
        when(mockDialogueService.formulateResponse(rgcCaptor.capture(), anyList()))
            .thenReturn("Echo: " + inputText);


        coreOrchestrationService.processIncomingText(inputText, userPlatformId, convoPlatformId, sourcePlatform);

        verify(mockActionExecutorService).executeAction(eq("echo"), eq(entities), isNull());
        assertThat(rgcCaptor.getValue().reason()).isEqualTo(ResponseReason.ACTION_EXECUTED);
        assertThat(rgcCaptor.getValue().mainPayload()).isEqualTo(actionResult);
    }

    @Test
    void processIncomingText_whenClarificationIsNeeded_shouldCallDialogueServiceInitiateClarification() {
        String inputText = "Create a task";
        StructuredNlpResult nlpResult = new StructuredNlpResult("CREATE_TASK", Collections.emptyMap(), inputText);
        when(mockNlpService.understandText(eq(inputText), anyList())).thenReturn(nlpResult);

        String clarificationQuestion = "What is the description of the task?";

        when(mockDialogueService.initiateClarification(eq(convoPlatformId), eq("CREATE_TASK"), anyList(), eq(nlpResult)))
            .thenReturn(clarificationQuestion); // This is what CoreOrchestrationService passes to formulateResponse

        ArgumentCaptor<ResponseGenerationContext> rgcCaptor = ArgumentCaptor.forClass(ResponseGenerationContext.class);
        when(mockDialogueService.formulateResponse(rgcCaptor.capture(), anyList()))
            .thenReturn(clarificationQuestion);


        String agentResponse = coreOrchestrationService.processIncomingText(inputText, userPlatformId, convoPlatformId, sourcePlatform);

        verify(mockDialogueService).initiateClarification(eq(convoPlatformId), eq("CREATE_TASK"), anyList(), eq(nlpResult));
        assertThat(agentResponse).isEqualTo(clarificationQuestion);

        ResponseGenerationContext capturedContext = rgcCaptor.getValue();
        assertThat(capturedContext.reason()).isEqualTo(ResponseReason.CLARIFICATION_NEEDED);
        assertThat(capturedContext.mainPayload()).isEqualTo(clarificationQuestion);
    }

    @Test
    void processIncomingText_whenInActiveClarification_shouldCallHandleClarificationInputAndReprocess() {
        String originalInput = "Create task"; // Input that initiated clarification
        String userInputDuringClarification = "The description is 'buy groceries'";

        DialogueState activeState = new DialogueState(convoPlatformId);
        StructuredNlpResult originalNlpResult = new StructuredNlpResult("CREATE_TASK", Collections.emptyMap(), originalInput);
        activeState.startClarification("CREATE_TASK", new ArrayList<>(List.of("description")), originalNlpResult);

        when(mockDialogueService.getDialogueState(convoPlatformId)).thenReturn(activeState);

        when(mockDialogueService.handleClarificationInput(eq(convoPlatformId), eq(userInputDuringClarification), eq(mockNlpService)))
            .thenAnswer(invocation -> {
                activeState.addClarifiedEntity("description", "buy groceries");
                return activeState;
            });

        TaskEntity createdTask = new TaskEntity();
        createdTask.setDescription("buy groceries");
        when(mockTaskService.createTask(eq("buy groceries"), eq(testUser), isNull(), isNull(), isNull(), eq(testConversation), isNull()))
            .thenReturn(createdTask);

        ArgumentCaptor<ResponseGenerationContext> rgcCaptor = ArgumentCaptor.forClass(ResponseGenerationContext.class);
        when(mockDialogueService.formulateResponse(rgcCaptor.capture(), anyList()))
            .thenReturn("Task created after clarification: buy groceries");


        coreOrchestrationService.processIncomingText(userInputDuringClarification, userPlatformId, convoPlatformId, sourcePlatform);

        verify(mockDialogueService).handleClarificationInput(eq(convoPlatformId), eq(userInputDuringClarification), eq(mockNlpService));
        verify(mockTaskService).createTask(eq("buy groceries"), eq(testUser), isNull(), isNull(), isNull(), eq(testConversation), isNull());
        verify(mockDialogueService).clearDialogueState(convoPlatformId);
        assertThat(rgcCaptor.getValue().reason()).isEqualTo(ResponseReason.TASK_CREATED);
    }

}
