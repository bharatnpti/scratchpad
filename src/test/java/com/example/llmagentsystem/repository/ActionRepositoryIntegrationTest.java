package com.example.llmagentsystem.repository;

import com.example.llmagentsystem.model.entity.ActionEntity;
import com.example.llmagentsystem.model.entity.PluginEntity;
import com.example.llmagentsystem.model.entity.TaskEntity;
import com.example.llmagentsystem.model.entity.UserEntity;
import com.example.llmagentsystem.model.enums.ActionStatus;
import com.example.llmagentsystem.model.enums.TaskStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ActionRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ActionRepository actionRepository;

    @Autowired
    private PluginRepository pluginRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;


    private PluginEntity testPlugin;
    private TaskEntity testTask;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws JsonProcessingException {
        UserEntity testUser = new UserEntity();
        testUser.setPlatformId("actionTestUser");
        testUser.setSourcePlatform("test");
        testUser.setEmail("actionTestUser@example.com");
        userRepository.save(testUser);
        entityManager.flush();

        PluginEntity plugin = new PluginEntity();
        plugin.setName("ActionTestPlugin");
        plugin.setInputSchema(objectMapper.writeValueAsString(List.of()));
        plugin.setOutputSchema(objectMapper.writeValueAsString(List.of()));
        plugin.setEntryPoint("com.example.ActionTestPlugin");
        plugin.setEntryPointType("java_class");
        testPlugin = pluginRepository.save(plugin);

        TaskEntity task = new TaskEntity();
        task.setDescription("Task for action");
        task.setCreator(testUser);
        task.setStatus(TaskStatus.PENDING);
        testTask = taskRepository.save(task);

        entityManager.flush();
    }

    @Test
    void whenSaveAction_thenFindById_returnsAction() throws JsonProcessingException {
        ActionEntity newAction = new ActionEntity();
        newAction.setPlugin(testPlugin);
        newAction.setTask(testTask);
        newAction.setStatus(ActionStatus.PENDING);
        newAction.setParameters(objectMapper.writeValueAsString(Map.of("param", "value")));
        newAction.setStartTime(OffsetDateTime.now());

        ActionEntity savedAction = actionRepository.save(newAction);
        entityManager.flush();
        entityManager.clear();

        Optional<ActionEntity> foundOpt = actionRepository.findById(savedAction.getActionId());

        assertThat(foundOpt).isPresent();
        ActionEntity found = foundOpt.get();
        assertThat(found.getStatus()).isEqualTo(ActionStatus.PENDING);
        assertThat(found.getPlugin().getPluginId()).isEqualTo(testPlugin.getPluginId());
        assertThat(found.getTask().getTaskId()).isEqualTo(testTask.getTaskId());
        assertThat(found.getParameters()).contains("param", "value");
        assertThat(found.getActionId()).isNotNull();
    }

    @Test
    void findByTaskTaskId_shouldReturnActionsForGivenTask() {
        ActionEntity action1 = new ActionEntity();
        action1.setPlugin(testPlugin);
        action1.setTask(testTask);
        action1.setStatus(ActionStatus.RUNNING);
        entityManager.persist(action1);

        ActionEntity action2 = new ActionEntity();
        action2.setPlugin(testPlugin);
        action2.setTask(testTask);
        action2.setStatus(ActionStatus.SUCCESS);
        entityManager.persist(action2);

        // Create another task and action to ensure filtering works
        TaskEntity anotherTask = new TaskEntity();
        anotherTask.setDescription("Another task");
        anotherTask.setCreator(testTask.getCreator()); // Re-use user for simplicity
        anotherTask.setStatus(TaskStatus.PENDING);
        taskRepository.save(anotherTask);
        entityManager.flush();

        ActionEntity action3ForAnotherTask = new ActionEntity();
        action3ForAnotherTask.setPlugin(testPlugin);
        action3ForAnotherTask.setTask(anotherTask);
        action3ForAnotherTask.setStatus(ActionStatus.PENDING);
        entityManager.persist(action3ForAnotherTask);

        entityManager.flush();

        List<ActionEntity> foundActions = actionRepository.findByTaskTaskId(testTask.getTaskId());

        assertThat(foundActions).hasSize(2);
        assertThat(foundActions).extracting(ActionEntity::getStatus).containsExactlyInAnyOrder(ActionStatus.RUNNING, ActionStatus.SUCCESS);
    }

    @Test
    void whenUpdateAction_fieldsAreUpdated() throws JsonProcessingException {
        ActionEntity action = new ActionEntity();
        action.setPlugin(testPlugin);
        action.setTask(testTask);
        action.setStatus(ActionStatus.PENDING);
        action.setStartTime(OffsetDateTime.now().minusMinutes(5));
        ActionEntity persistedAction = entityManager.persistFlushFind(action); // Persist, flush, find

        persistedAction.setStatus(ActionStatus.SUCCESS);
        persistedAction.setEndTime(OffsetDateTime.now());
        persistedAction.setOutput(objectMapper.writeValueAsString(Map.of("result", "done")));
        persistedAction.setExecutionLog("Completed successfully.");

        actionRepository.save(persistedAction);
        entityManager.flush();
        entityManager.clear();

        Optional<ActionEntity> updatedActionOpt = actionRepository.findById(persistedAction.getActionId());
        assertThat(updatedActionOpt).isPresent();
        ActionEntity updatedAction = updatedActionOpt.get();
        assertThat(updatedAction.getStatus()).isEqualTo(ActionStatus.SUCCESS);
        assertThat(updatedAction.getEndTime()).isNotNull();
        assertThat(updatedAction.getOutput()).contains("result", "done");
        assertThat(updatedAction.getExecutionLog()).isEqualTo("Completed successfully.");
    }
}
