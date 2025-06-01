package com.example.llmagentsystem.service;

import com.example.llmagentsystem.model.entity.ConversationEntity;
import com.example.llmagentsystem.model.entity.TaskEntity;
import com.example.llmagentsystem.model.entity.TaskHistoryEntity;
import com.example.llmagentsystem.model.entity.UserEntity;
import com.example.llmagentsystem.model.enums.TaskPriority;
import com.example.llmagentsystem.model.enums.TaskStatus;
import com.example.llmagentsystem.repository.TaskHistoryRepository;
import com.example.llmagentsystem.repository.TaskRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskHistoryRepository taskHistoryRepository;

    @Spy // Use Spy for ObjectMapper to allow real serialization but mock if needed
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private TaskService taskService;

    private UserEntity testUser;
    private ConversationEntity testConversation;

    @BeforeEach
    void setUp() {
        testUser = new UserEntity();
        testUser.setUserId(UUID.randomUUID());
        testUser.setDisplayName("Test User");

        testConversation = new ConversationEntity();
        testConversation.setConversationId(UUID.randomUUID());
        // taskService is automatically created with mocks due to @InjectMocks
    }

    @Test
    void createTask_shouldSaveTaskAndLogHistory() throws JsonProcessingException {
        String description = "Test Task Description";
        OffsetDateTime dueDate = OffsetDateTime.now().plusDays(1);

        // TaskEntity taskToSave = new TaskEntity(); // Not needed, service creates it.
        // taskToSave.setDescription(description);

        // Mocking behavior for save operation
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> {
            TaskEntity saved = invocation.getArgument(0);
            saved.setTaskId(UUID.randomUUID()); // Simulate ID generation
            saved.setCreatedAt(OffsetDateTime.now());
            saved.setUpdatedAt(OffsetDateTime.now());
            return saved;
        });
        when(taskHistoryRepository.save(any(TaskHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskEntity createdTask = taskService.createTask(description, testUser, null, dueDate, TaskPriority.HIGH, testConversation, "http://link.com");

        assertThat(createdTask).isNotNull();
        assertThat(createdTask.getDescription()).isEqualTo(description);
        assertThat(createdTask.getCreator()).isEqualTo(testUser);
        assertThat(createdTask.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(createdTask.getDueDate()).isEqualTo(dueDate);
        assertThat(createdTask.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(createdTask.getConversationContext()).isEqualTo(testConversation);
        assertThat(createdTask.getPlatformContextLink()).isEqualTo("http://link.com");

        ArgumentCaptor<TaskEntity> taskCaptor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository, times(1)).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getDescription()).isEqualTo(description);

        ArgumentCaptor<TaskHistoryEntity> historyCaptor = ArgumentCaptor.forClass(TaskHistoryEntity.class);
        verify(taskHistoryRepository, times(1)).save(historyCaptor.capture());
        TaskHistoryEntity historyLog = historyCaptor.getValue();
        assertThat(historyLog.getEventType()).isEqualTo("CREATED");
        assertThat(historyLog.getTask()).isEqualTo(createdTask);
        assertThat(historyLog.getUserWhoChanged()).isEqualTo(testUser);

        Map<String, String> expectedDetails = Map.of("description", description, "status", TaskStatus.PENDING.name());
        String expectedDetailsJson = objectMapper.writeValueAsString(expectedDetails);
        assertThat(historyLog.getChangeDetails()).isEqualTo(expectedDetailsJson);
    }

    @Test
    void updateTaskStatus_whenTaskExists_shouldUpdateStatusAndLogHistory() throws JsonProcessingException {
        UUID taskId = UUID.randomUUID();
        TaskEntity existingTask = new TaskEntity();
        existingTask.setTaskId(taskId);
        existingTask.setStatus(TaskStatus.PENDING);
        existingTask.setCreator(testUser);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(existingTask));
        when(taskRepository.save(any(TaskEntity.class))).thenReturn(existingTask);
        when(taskHistoryRepository.save(any(TaskHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));


        Optional<TaskEntity> updatedTaskOpt = taskService.updateTaskStatus(taskId, TaskStatus.IN_PROGRESS, testUser);

        assertThat(updatedTaskOpt).isPresent();
        TaskEntity updatedTask = updatedTaskOpt.get();
        assertThat(updatedTask.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);

        verify(taskRepository, times(1)).save(existingTask);

        ArgumentCaptor<TaskHistoryEntity> historyCaptor = ArgumentCaptor.forClass(TaskHistoryEntity.class);
        verify(taskHistoryRepository, times(1)).save(historyCaptor.capture());
        TaskHistoryEntity historyLog = historyCaptor.getValue();
        assertThat(historyLog.getEventType()).isEqualTo("STATUS_CHANGED");
        assertThat(historyLog.getTask()).isEqualTo(updatedTask);
        assertThat(historyLog.getUserWhoChanged()).isEqualTo(testUser);

        Map<String, String> expectedDetails = Map.of("old_status", TaskStatus.PENDING.name(), "new_status", TaskStatus.IN_PROGRESS.name());
        String expectedDetailsJson = objectMapper.writeValueAsString(expectedDetails);
        assertThat(historyLog.getChangeDetails()).isEqualTo(expectedDetailsJson);
    }

    @Test
    void updateTaskStatus_whenTaskNotFound_shouldReturnEmpty() {
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        Optional<TaskEntity> result = taskService.updateTaskStatus(taskId, TaskStatus.COMPLETED, testUser);

        assertThat(result).isEmpty();
        verify(taskRepository, never()).save(any());
        verify(taskHistoryRepository, never()).save(any());
    }

    @Test
    void assignTask_whenTaskExists_shouldUpdateAssigneeAndLogHistory() throws JsonProcessingException {
        UUID taskId = UUID.randomUUID();
        TaskEntity existingTask = new TaskEntity();
        existingTask.setTaskId(taskId);
        existingTask.setAssignee(null);

        UserEntity newAssignee = new UserEntity();
        newAssignee.setUserId(UUID.randomUUID());
        newAssignee.setDisplayName("New Assignee");

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(existingTask));
        when(taskRepository.save(any(TaskEntity.class))).thenReturn(existingTask);
        when(taskHistoryRepository.save(any(TaskHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<TaskEntity> updatedTaskOpt = taskService.assignTask(taskId, newAssignee, testUser);

        assertThat(updatedTaskOpt).isPresent();
        assertThat(updatedTaskOpt.get().getAssignee()).isEqualTo(newAssignee);

        ArgumentCaptor<TaskHistoryEntity> historyCaptor = ArgumentCaptor.forClass(TaskHistoryEntity.class);
        verify(taskHistoryRepository).save(historyCaptor.capture());
        TaskHistoryEntity historyLog = historyCaptor.getValue();

        assertThat(historyLog.getEventType()).isEqualTo("ASSIGNEE_CHANGED");
        Map<String, String> expectedDetails = Map.of(
            "old_assignee_id", "None",
            "new_assignee_id", newAssignee.getUserId().toString()
        );
        assertThat(historyLog.getChangeDetails()).isEqualTo(objectMapper.writeValueAsString(expectedDetails));
    }


    @Test
    void addTaskComment_shouldSaveHistoryEvent() throws JsonProcessingException {
        UUID taskId = UUID.randomUUID();
        TaskEntity existingTask = new TaskEntity();
        existingTask.setTaskId(taskId);
        String commentText = "This is a test comment.";

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(existingTask));
        when(taskHistoryRepository.save(any(TaskHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskHistoryEntity commentHistory = taskService.addTaskComment(taskId, testUser, commentText);

        assertThat(commentHistory).isNotNull();
        assertThat(commentHistory.getEventType()).isEqualTo("COMMENT_ADDED");
        assertThat(commentHistory.getTask()).isEqualTo(existingTask);
        assertThat(commentHistory.getUserWhoChanged()).isEqualTo(testUser);

        Map<String, String> expectedDetails = Map.of("comment", commentText);
        assertThat(commentHistory.getChangeDetails()).isEqualTo(objectMapper.writeValueAsString(expectedDetails));

        verify(taskHistoryRepository, times(1)).save(any(TaskHistoryEntity.class));
    }

    @Test
    void logTaskHistory_whenJsonProcessingFails_shouldLogFallbackDetails() throws JsonProcessingException {
        TaskEntity task = new TaskEntity();
        task.setTaskId(UUID.randomUUID());
        UserEntity user = new UserEntity(); // testUser could be used here
        Map<String, String> details = Map.of("key", "value");

        // Force ObjectMapper to fail during serialization for this specific call
        ObjectMapper localMockMapper = mock(ObjectMapper.class); // Create a local mock for this test
        when(localMockMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Test Error"){});

        // Create a TaskService instance with the localMockMapper for this specific test
        TaskService serviceWithMockMapper = new TaskService(taskRepository, taskHistoryRepository);
        // Reflection or a setter would be needed to inject the mock ObjectMapper into the @Spy field
        // Or, better, make ObjectMapper a constructor parameter that can be mocked easily.
        // For the existing structure with @Spy and @InjectMocks, this is harder.
        // Let's assume the @Spy objectMapper is used and we can't easily make it throw for one call.
        // The original code uses the @Spy objectMapper.
        // To test this path, we'd need to make objectMapper.writeValueAsString throw an exception.
        // This can be done with: doThrow(new JsonProcessingException("..."){}).when(objectMapper).writeValueAsString(any());

        doThrow(new JsonProcessingException("Serialization error"){}).when(objectMapper).writeValueAsString(details);

        // Need to call the private method logTaskHistory indirectly or make it package-private/protected for testing.
        // For now, let's call a public method that uses it, e.g., addTaskComment, and check the outcome.
        // Or, if testing the private method directly was the goal (not ideal for pure unit tests):
        // For this example, we'll call a public method that triggers the history logging.
        when(taskRepository.findById(any(UUID.class))).thenReturn(Optional.of(task));
        when(taskHistoryRepository.save(any(TaskHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));


        TaskHistoryEntity historyWithJsonError = taskService.addTaskComment(task.getTaskId(), user, "Comment that triggers json error");

        assertThat(historyWithJsonError.getChangeDetails()).isEqualTo("{\"error\":\"Could not serialize details\"}");

        // Clean up mock behavior for objectMapper if it's a @Spy and shared across tests
        reset(objectMapper); // Reset the spy to avoid affecting other tests
         // Re-register the module as reset might clear it if it was a real spy modifying the instance
        objectMapper.registerModule(new JavaTimeModule());
    }

}
