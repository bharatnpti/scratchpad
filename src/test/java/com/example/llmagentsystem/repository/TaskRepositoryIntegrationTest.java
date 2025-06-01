package com.example.llmagentsystem.repository;

import com.example.llmagentsystem.model.entity.TaskEntity;
import com.example.llmagentsystem.model.entity.UserEntity;
import com.example.llmagentsystem.model.enums.TaskStatus;
import com.example.llmagentsystem.model.enums.TaskPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class TaskRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired // Need UserRepository to create dependent UserEntity instances
    private UserRepository userRepository;

    private UserEntity testCreator;
    private UserEntity testAssignee;

    @BeforeEach
    void setUp() {
        // It's better to save users via repository if @DataJpaTest doesn't cascade persist well with entityManager alone
        // or if UserEntity has complex lifecycle/defaults set by repository/service layer not covered by simple persist.
        // For simple cases, entityManager.persist is fine.
        UserEntity creator = new UserEntity();
        creator.setPlatformId("creator1");
        creator.setSourcePlatform("test");
        creator.setEmail("creator1@example.com"); // Ensure email is unique if constrained
        testCreator = userRepository.save(creator); // Use repository to ensure it's managed

        UserEntity assignee = new UserEntity();
        assignee.setPlatformId("assignee1");
        assignee.setSourcePlatform("test");
        assignee.setEmail("assignee1@example.com"); // Ensure email is unique
        testAssignee = userRepository.save(assignee);

        entityManager.flush(); // Ensure users are persisted before tasks reference them
    }

    @Test
    void whenSaveTask_thenFindById_returnsTask() {
        TaskEntity newTask = new TaskEntity();
        newTask.setDescription("New test task");
        newTask.setCreator(testCreator);
        newTask.setAssignee(testAssignee);
        newTask.setStatus(TaskStatus.PENDING);
        newTask.setPriority(TaskPriority.MEDIUM);
        newTask.setDueDate(OffsetDateTime.now().plusDays(5));

        TaskEntity savedTask = taskRepository.save(newTask);
        entityManager.flush();
        entityManager.clear();

        Optional<TaskEntity> foundTaskOpt = taskRepository.findById(savedTask.getTaskId());

        assertThat(foundTaskOpt).isPresent();
        TaskEntity foundTask = foundTaskOpt.get();
        assertThat(foundTask.getDescription()).isEqualTo("New test task");
        assertThat(foundTask.getCreator().getUserId()).isEqualTo(testCreator.getUserId());
        assertThat(foundTask.getAssignee().getUserId()).isEqualTo(testAssignee.getUserId());
        assertThat(foundTask.getTaskId()).isNotNull();
    }

    @Test
    void findByAssigneeUserIdAndStatus_shouldReturnMatchingTasks() {
        TaskEntity task1 = new TaskEntity();
        task1.setDescription("Task 1 for assignee");
        task1.setCreator(testCreator);
        task1.setAssignee(testAssignee);
        task1.setStatus(TaskStatus.IN_PROGRESS);
        entityManager.persist(task1);

        TaskEntity task2 = new TaskEntity();
        task2.setDescription("Task 2 for assignee - different status");
        task2.setCreator(testCreator);
        task2.setAssignee(testAssignee);
        task2.setStatus(TaskStatus.PENDING);
        entityManager.persist(task2);

        UserEntity anotherAssignee = new UserEntity();
        anotherAssignee.setPlatformId("assignee2");
        anotherAssignee.setSourcePlatform("test");
        anotherAssignee.setEmail("assignee2@example.com"); // Unique email
        userRepository.save(anotherAssignee);
        entityManager.flush();

        TaskEntity task3 = new TaskEntity();
        task3.setDescription("Task 3 for another assignee");
        task3.setCreator(testCreator);
        task3.setAssignee(anotherAssignee);
        task3.setStatus(TaskStatus.IN_PROGRESS);
        entityManager.persist(task3);

        entityManager.flush();

        List<TaskEntity> foundTasks = taskRepository.findByAssigneeUserIdAndStatus(testAssignee.getUserId(), TaskStatus.IN_PROGRESS);

        assertThat(foundTasks).hasSize(1);
        assertThat(foundTasks.get(0).getDescription()).isEqualTo("Task 1 for assignee");
    }

    @Test
    void findByStatusAndDueDateBefore_shouldReturnMatchingTasks() {
        OffsetDateTime now = OffsetDateTime.now();

        TaskEntity taskDueSoonPending = new TaskEntity();
        taskDueSoonPending.setDescription("Due soon, pending");
        taskDueSoonPending.setCreator(testCreator);
        taskDueSoonPending.setStatus(TaskStatus.PENDING);
        taskDueSoonPending.setDueDate(now.plusDays(1));
        entityManager.persist(taskDueSoonPending);

        TaskEntity taskDueSoonInProgress = new TaskEntity();
        taskDueSoonInProgress.setDescription("Due soon, in progress");
        taskDueSoonInProgress.setCreator(testCreator);
        taskDueSoonInProgress.setStatus(TaskStatus.IN_PROGRESS);
        taskDueSoonInProgress.setDueDate(now.plusDays(1));
        entityManager.persist(taskDueSoonInProgress);

        TaskEntity taskDueLaterPending = new TaskEntity();
        taskDueLaterPending.setDescription("Due later, pending");
        taskDueLaterPending.setCreator(testCreator);
        taskDueLaterPending.setStatus(TaskStatus.PENDING);
        taskDueLaterPending.setDueDate(now.plusDays(10));
        entityManager.persist(taskDueLaterPending);

        entityManager.flush();

        List<TaskEntity> foundTasks = taskRepository.findByStatusAndDueDateBefore(TaskStatus.PENDING, now.plusDays(3));

        assertThat(foundTasks).hasSize(1);
        assertThat(foundTasks.get(0).getDescription()).isEqualTo("Due soon, pending");
    }
}
