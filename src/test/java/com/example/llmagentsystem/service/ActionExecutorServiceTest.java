package com.example.llmagentsystem.service;

import com.example.llmagentsystem.model.entity.ActionEntity;
import com.example.llmagentsystem.model.entity.PluginEntity;
import com.example.llmagentsystem.model.entity.TaskEntity;
import com.example.llmagentsystem.model.enums.ActionStatus;
import com.example.llmagentsystem.plugin.PluginInterface;
import com.example.llmagentsystem.plugin.PluginParameter;
import com.example.llmagentsystem.plugin.PluginSchema;
import com.example.llmagentsystem.repository.ActionRepository;
import com.example.llmagentsystem.repository.PluginRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import org.springframework.context.ApplicationContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActionExecutorServiceTest {

    @Mock
    private PluginRepository pluginRepository;

    @Mock
    private ActionRepository actionRepository;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private TaskService mockTaskService; // To mock task retrieval

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private ActionExecutorService actionExecutorService;

    private PluginEntity calculatorPluginEntity;
    private PluginInterface mockCalculatorPlugin;
    private TaskEntity mockTask;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        calculatorPluginEntity = new PluginEntity();
        calculatorPluginEntity.setPluginId(UUID.randomUUID());
        calculatorPluginEntity.setName("calculator");
        calculatorPluginEntity.setEnabled(true);
        calculatorPluginEntity.setEntryPointType("spring_bean"); // or "java_class"
        calculatorPluginEntity.setEntryPoint("calculatorPluginBean"); // Bean name or class FQN

        // Basic schema for calculator
        List<PluginParameter> inputParams = List.of(new PluginParameter("operand1", "number", "First number", true));
        List<PluginParameter> outputParams = List.of(new PluginParameter("result", "number", "Calculation result", true));
        calculatorPluginEntity.setInputSchema(objectMapper.writeValueAsString(inputParams));
        calculatorPluginEntity.setOutputSchema(objectMapper.writeValueAsString(outputParams));


        mockCalculatorPlugin = mock(PluginInterface.class);
        // Mock schema if needed for validation tests (not strictly needed if only testing execution path)
        // PluginSchema schema = new PluginSchema("calculator", "desc", inputParams, outputParams);
        // when(mockCalculatorPlugin.getSchema()).thenReturn(schema);

        mockTask = new TaskEntity();
        mockTask.setTaskId(UUID.randomUUID());
    }

    @Test
    void executeAction_whenPluginExistsAndEnabled_shouldExecuteAndLogAction() throws JsonProcessingException {
        String pluginName = "calculator";
        Map<String, Object> params = Map.of("operand1", 10.0, "operand2", 5.0, "operation", "add");
        Map<String, Object> expectedResult = Map.of("sum", 15.0);
        UUID taskId = mockTask.getTaskId();

        when(pluginRepository.findByName(pluginName)).thenReturn(Optional.of(calculatorPluginEntity));
        // Correctly mock TaskService bean retrieval from ApplicationContext
        when(applicationContext.getBean(TaskService.class)).thenReturn(mockTaskService);
        when(mockTaskService.getTask(taskId)).thenReturn(Optional.of(mockTask)); // Task exists

        when(applicationContext.getBean(calculatorPluginEntity.getEntryPoint(), PluginInterface.class))
            .thenReturn(mockCalculatorPlugin);
        when(mockCalculatorPlugin.execute(params)).thenReturn(expectedResult);
        when(actionRepository.save(any(ActionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> actualResult = actionExecutorService.executeAction(pluginName, params, taskId);

        assertThat(actualResult).isEqualTo(expectedResult);

        ArgumentCaptor<ActionEntity> actionEntityCaptor = ArgumentCaptor.forClass(ActionEntity.class);
        verify(actionRepository, times(2)).save(actionEntityCaptor.capture()); // Once for RUNNING, once for final status

        ActionEntity loggedAction = actionEntityCaptor.getAllValues().get(1); // Get the final saved entity
        assertThat(loggedAction.getPlugin()).isEqualTo(calculatorPluginEntity);
        assertThat(loggedAction.getTask()).isEqualTo(mockTask);
        assertThat(loggedAction.getStatus()).isEqualTo(ActionStatus.SUCCESS);
        assertThat(loggedAction.getParameters()).isEqualTo(objectMapper.writeValueAsString(params));
        assertThat(loggedAction.getOutput()).isEqualTo(objectMapper.writeValueAsString(expectedResult));
        assertThat(loggedAction.getStartTime()).isNotNull();
        assertThat(loggedAction.getEndTime()).isNotNull();
    }

    @Test
    void executeAction_whenPluginNotFound_shouldThrowIllegalArgumentException() {
        String pluginName = "nonexistentPlugin";
        when(pluginRepository.findByName(pluginName)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> actionExecutorService.executeAction(pluginName, Collections.emptyMap(), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Plugin 'nonexistentPlugin' not found or is disabled.");
        verify(actionRepository, never()).save(any());
    }

    @Test
    void executeAction_whenPluginDisabled_shouldThrowIllegalArgumentException() {
        String pluginName = "disabledPlugin";
        calculatorPluginEntity.setEnabled(false); // Disable the plugin
        when(pluginRepository.findByName(pluginName)).thenReturn(Optional.of(calculatorPluginEntity));

        assertThatThrownBy(() -> actionExecutorService.executeAction(pluginName, Collections.emptyMap(), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Plugin 'disabledPlugin' not found or is disabled.");
    }

    @Test
    void executeAction_whenPluginExecutionFails_shouldLogFailedActionAndThrowRuntimeException() {
        String pluginName = "calculator";
        Map<String, Object> params = Map.of("operand1", 10.0); // Missing operand2

        when(pluginRepository.findByName(pluginName)).thenReturn(Optional.of(calculatorPluginEntity));
        when(applicationContext.getBean(TaskService.class)).thenReturn(mockTaskService);
        when(mockTaskService.getTask(any())).thenReturn(Optional.of(mockTask));
        when(applicationContext.getBean(calculatorPluginEntity.getEntryPoint(), PluginInterface.class))
            .thenReturn(mockCalculatorPlugin);
        when(mockCalculatorPlugin.execute(params)).thenThrow(new IllegalArgumentException("Missing parameter operand2"));
        when(actionRepository.save(any(ActionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));


        assertThatThrownBy(() -> actionExecutorService.executeAction(pluginName, params, mockTask.getTaskId()))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Plugin execution failed: Missing parameter operand2");

        ArgumentCaptor<ActionEntity> actionEntityCaptor = ArgumentCaptor.forClass(ActionEntity.class);
        verify(actionRepository, times(2)).save(actionEntityCaptor.capture());
        ActionEntity loggedAction = actionEntityCaptor.getAllValues().get(1);
        assertThat(loggedAction.getStatus()).isEqualTo(ActionStatus.FAILED);
        assertThat(loggedAction.getExecutionLog()).isEqualTo("Missing parameter operand2");
    }

    @Test
    void registerPlugin_shouldSavePluginEntity() throws JsonProcessingException {
        PluginSchema schema = new PluginSchema("testPlugin", "A test plugin",
            List.of(new PluginParameter("input", "string", "Input", true)),
            List.of(new PluginParameter("output", "string", "Output", true))
        );
        String entryPoint = "com.example.TestPlugin";
        String entryPointType = "java_class";

        when(pluginRepository.findByName("testPlugin")).thenReturn(Optional.empty());
        when(pluginRepository.save(any(PluginEntity.class))).thenAnswer(invocation -> {
            PluginEntity saved = invocation.getArgument(0);
            saved.setPluginId(UUID.randomUUID()); // Simulate ID generation
            return saved;
        });

        PluginEntity registeredPlugin = actionExecutorService.registerPlugin(schema, entryPoint, entryPointType, true);

        assertThat(registeredPlugin).isNotNull();
        assertThat(registeredPlugin.getName()).isEqualTo("testPlugin");
        assertThat(registeredPlugin.getDescription()).isEqualTo("A test plugin");
        assertThat(registeredPlugin.getEntryPoint()).isEqualTo(entryPoint);
        assertThat(registeredPlugin.getEntryPointType()).isEqualTo(entryPointType);
        assertThat(registeredPlugin.isEnabled()).isTrue();

        assertThat(registeredPlugin.getInputSchema()).contains("\"name\":\"input\"");
        assertThat(registeredPlugin.getOutputSchema()).contains("\"name\":\"output\"");

        verify(pluginRepository).save(any(PluginEntity.class));
    }

    @Test
    void listAvailablePlugins_shouldReturnListOfEnabledPluginSchemas() throws JsonProcessingException {
        PluginEntity plugin1 = new PluginEntity();
        plugin1.setName("pluginOne");
        plugin1.setDescription("First plugin");
        plugin1.setEnabled(true);
        plugin1.setInputSchema(objectMapper.writeValueAsString(List.of(new PluginParameter("p1_in", "string", "", true))));
        plugin1.setOutputSchema(objectMapper.writeValueAsString(List.of(new PluginParameter("p1_out", "string", "", true))));

        PluginEntity plugin2 = new PluginEntity();
        plugin2.setName("pluginTwo");
        plugin2.setDescription("Second plugin (disabled)");
        plugin2.setEnabled(false);
        plugin2.setInputSchema(objectMapper.writeValueAsString(List.of()));
        plugin2.setOutputSchema(objectMapper.writeValueAsString(List.of()));

        PluginEntity plugin3 = new PluginEntity();
        plugin3.setName("pluginThree");
        plugin3.setDescription("Third plugin");
        plugin3.setEnabled(true);
        plugin3.setInputSchema(objectMapper.writeValueAsString(List.of(new PluginParameter("p3_in", "string", "", true))));
        plugin3.setOutputSchema(objectMapper.writeValueAsString(List.of(new PluginParameter("p3_out", "string", "", true))));


        when(pluginRepository.findAll()).thenReturn(List.of(plugin1, plugin2, plugin3));

        List<PluginSchema> schemas = actionExecutorService.listAvailablePlugins();

        assertThat(schemas).hasSize(2);
        assertThat(schemas.stream().map(PluginSchema::getPluginName)).containsExactlyInAnyOrder("pluginOne", "pluginThree");
        assertThat(schemas.get(0).getInputParameters().get(0).getName()).isEqualTo("p1_in");
    }

    @Test
    void listAvailablePlugins_whenSchemaDeserializationFails_shouldReturnSchemaWithError() throws JsonProcessingException {
        PluginEntity pluginWithError = new PluginEntity();
        pluginWithError.setName("errorPlugin");
        pluginWithError.setDescription("Plugin with bad schema");
        pluginWithError.setEnabled(true);
        pluginWithError.setInputSchema("this is not json");
        pluginWithError.setOutputSchema(objectMapper.writeValueAsString(List.of()));


        when(pluginRepository.findAll()).thenReturn(List.of(pluginWithError));

        List<PluginSchema> schemas = actionExecutorService.listAvailablePlugins();

        assertThat(schemas).hasSize(1);
        PluginSchema errorSchema = schemas.get(0);
        assertThat(errorSchema.getPluginName()).isEqualTo("errorPlugin");
        assertThat(errorSchema.getDescription()).isEqualTo("Error: Could not parse schema");
        assertThat(errorSchema.getInputParameters()).isEmpty();
    }

}
