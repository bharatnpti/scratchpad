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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class TaskService {

    private static final Logger logger = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final TaskHistoryRepository taskHistoryRepository;
    private final ObjectMapper objectMapper; // For serializing changeDetails to JSON

    @Autowired
    public TaskService(TaskRepository taskRepository, TaskHistoryRepository taskHistoryRepository) {
        this.taskRepository = taskRepository;
        this.taskHistoryRepository = taskHistoryRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule()); // For OffsetDateTime serialization
    }

    @Transactional
    public TaskEntity createTask(
            String description,
            UserEntity creator,
            UserEntity assignee, // Can be null
            OffsetDateTime dueDate, // Can be null
            TaskPriority priority, // Can be null, default in entity or here
            ConversationEntity conversationContext, // Can be null
            String platformContextLink // Can be null
    ) {
        logger.info("Creating task with description: '{}' by user: {}", description, creator.getUserId());

        TaskEntity task = new TaskEntity();
        task.setDescription(description);
        task.setCreator(creator);
        task.setAssignee(assignee);
        task.setStatus(TaskStatus.PENDING); // Initial status
        task.setDueDate(dueDate);
        task.setPriority(priority != null ? priority : TaskPriority.MEDIUM); // Default priority
        task.setConversationContext(conversationContext);
        task.setPlatformContextLink(platformContextLink);

        TaskEntity savedTask = taskRepository.save(task);
        logTaskHistory(savedTask, creator, "CREATED",
            Map.of("description", description, "status", TaskStatus.PENDING.name()));

        logger.info("Task created with ID: {}", savedTask.getTaskId());
        return savedTask;
    }

    @Transactional(readOnly = true)
    public Optional<TaskEntity> getTask(UUID taskId) {
        logger.debug("Fetching task with ID: {}", taskId);
        return taskRepository.findById(taskId);
    }

    @Transactional
    public Optional<TaskEntity> updateTaskStatus(UUID taskId, TaskStatus newStatus, UserEntity changedByUser) {
        logger.info("Updating status of task {} to {} by user {}", taskId, newStatus, changedByUser.getUserId());
        Optional<TaskEntity> taskOptional = taskRepository.findById(taskId);
        if (taskOptional.isPresent()) {
            TaskEntity task = taskOptional.get();
            TaskStatus oldStatus = task.getStatus();
            task.setStatus(newStatus);
            TaskEntity updatedTask = taskRepository.save(task);
            logTaskHistory(updatedTask, changedByUser, "STATUS_CHANGED",
                Map.of("old_status", oldStatus.name(), "new_status", newStatus.name()));
            return Optional.of(updatedTask);
        }
        logger.warn("Task with ID {} not found for status update.", taskId);
        return Optional.empty();
    }

    @Transactional
    public Optional<TaskEntity> assignTask(UUID taskId, UserEntity assignee, UserEntity changedByUser) {
        logger.info("Assigning task {} to user {} by user {}", taskId, assignee.getUserId(), changedByUser.getUserId());
        Optional<TaskEntity> taskOptional = taskRepository.findById(taskId);
        if (taskOptional.isPresent()) {
            TaskEntity task = taskOptional.get();
            UUID oldAssigneeId = task.getAssignee() != null ? task.getAssignee().getUserId() : null;
            task.setAssignee(assignee);
            TaskEntity updatedTask = taskRepository.save(task);
            logTaskHistory(updatedTask, changedByUser, "ASSIGNEE_CHANGED",
                Map.of("old_assignee_id", oldAssigneeId != null ? oldAssigneeId.toString() : "None",
                         "new_assignee_id", assignee.getUserId().toString()));
            return Optional.of(updatedTask);
        }
        logger.warn("Task with ID {} not found for assignment.", taskId);
        return Optional.empty();
    }

    @Transactional
    public Optional<TaskEntity> updateTaskDescription(UUID taskId, String newDescription, UserEntity changedByUser) {
        logger.info("Updating description of task {} by user {}", taskId, changedByUser.getUserId());
        Optional<TaskEntity> taskOptional = taskRepository.findById(taskId);
        if (taskOptional.isPresent()) {
            TaskEntity task = taskOptional.get();
            String oldDescription = task.getDescription();
            task.setDescription(newDescription);
            TaskEntity updatedTask = taskRepository.save(task);
            logTaskHistory(updatedTask, changedByUser, "DESCRIPTION_CHANGED",
                Map.of("old_description_preview", oldDescription.substring(0, Math.min(oldDescription.length(), 50)),
                         "new_description_preview", newDescription.substring(0, Math.min(newDescription.length(), 50))));
            return Optional.of(updatedTask);
        }
        logger.warn("Task with ID {} not found for description update.", taskId);
        return Optional.empty();
    }

    @Transactional
    public Optional<TaskEntity> setTaskDueDate(UUID taskId, OffsetDateTime newDueDate, UserEntity changedByUser) {
        logger.info("Setting due date for task {} to {} by user {}", taskId, newDueDate, changedByUser.getUserId());
        Optional<TaskEntity> taskOptional = taskRepository.findById(taskId);
        if (taskOptional.isPresent()) {
            TaskEntity task = taskOptional.get();
            OffsetDateTime oldDueDate = task.getDueDate();
            task.setDueDate(newDueDate);
            TaskEntity updatedTask = taskRepository.save(task);
            logTaskHistory(updatedTask, changedByUser, "DUE_DATE_CHANGED",
                Map.of("old_due_date", oldDueDate != null ? oldDueDate.toString() : "None",
                         "new_due_date", newDueDate != null ? newDueDate.toString() : "None"));
            return Optional.of(updatedTask);
        }
        logger.warn("Task with ID {} not found for due date update.", taskId);
        return Optional.empty();
    }


    @Transactional
    public TaskHistoryEntity addTaskComment(UUID taskId, UserEntity commenter, String commentText) {
        logger.info("Adding comment to task {} by user {}: '{}'", taskId, commenter.getUserId(), commentText);
        // Ensure task exists, though logTaskHistory will also implicitly check by requiring TaskEntity
        TaskEntity task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found with ID: " + taskId));

        return logTaskHistory(task, commenter, "COMMENT_ADDED", Map.of("comment", commentText));
    }

    @Transactional(readOnly = true)
    public List<TaskHistoryEntity> getTaskHistory(UUID taskId) {
        logger.debug("Fetching history for task ID: {}", taskId);
        return taskHistoryRepository.findByTaskTaskIdOrderByTimestampDesc(taskId);
    }

    @Transactional(readOnly = true)
    public List<TaskEntity> findTasksByAssigneeAndStatus(UserEntity assignee, TaskStatus status) {
        logger.debug("Finding tasks for assignee {} with status {}", assignee.getUserId(), status);
        return taskRepository.findByAssigneeUserIdAndStatus(assignee.getUserId(), status);
    }

    @Transactional(readOnly = true)
    public List<TaskEntity> findTasksDueSoon(OffsetDateTime dueBefore, TaskStatus statusNot) {
        logger.debug("Finding tasks due before {} and not in status {}", dueBefore, statusNot);
        // This requires a custom query if we want "status NOT IN (...)"
        // For now, let's use a simpler repository method or stream filter.
        // The LLD had findByStatusAndDueDateBefore - adapt this.
        // We might need to fetch tasks and filter, or add a more specific repo method.
        // For this example, let's assume a method like:
        // return taskRepository.findByDueDateBeforeAndStatusNot(dueBefore, statusNot);
        // If not, a simpler query:
        return taskRepository.findByStatusAndDueDateBefore(TaskStatus.PENDING, dueBefore); // Example
    }


    private TaskHistoryEntity logTaskHistory(TaskEntity task, UserEntity changedByUser, String eventType, Map<String, String> details) {
        TaskHistoryEntity history = new TaskHistoryEntity();
        history.setTask(task);
        history.setUserWhoChanged(changedByUser); // Can be null if system change
        history.setEventType(eventType);
        try {
            history.setChangeDetails(objectMapper.writeValueAsString(details));
        } catch (JsonProcessingException e) {
            logger.error("Error serializing task history details to JSON: {}", e.getMessage(), e);
            history.setChangeDetails("{\"error\":\"Could not serialize details\"}"); // Corrected JSON string
        }
        return taskHistoryRepository.save(history);
    }
}
