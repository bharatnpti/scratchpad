package com.example.llmagentsystem.service;

import com.example.llmagentsystem.model.entity.ActionEntity;
import com.example.llmagentsystem.model.entity.PluginEntity;
import com.example.llmagentsystem.model.enums.ActionStatus;
import com.example.llmagentsystem.plugin.PluginInterface;
import com.example.llmagentsystem.plugin.PluginParameter;
import com.example.llmagentsystem.plugin.PluginSchema;
import com.example.llmagentsystem.repository.ActionRepository;
import com.example.llmagentsystem.repository.PluginRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ActionExecutorService {

    private static final Logger logger = LoggerFactory.getLogger(ActionExecutorService.class);

    private final PluginRepository pluginRepository;
    private final ActionRepository actionRepository;
    private final ApplicationContext applicationContext; // To get Spring beans by name/type
    private final ObjectMapper objectMapper; // For serializing/deserializing parameters and output

    @Autowired
    public ActionExecutorService(PluginRepository pluginRepository,
                                 ActionRepository actionRepository,
                                 ApplicationContext applicationContext,
                                 ObjectMapper objectMapper) {
        this.pluginRepository = pluginRepository;
        this.actionRepository = actionRepository;
        this.applicationContext = applicationContext;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes an action identified by its plugin name and parameters.
     *
     * @param pluginName The unique name of the plugin to execute.
     * @param parametersMap A map of parameters for the plugin.
     * @param triggerringTaskId Optional UUID of the task that triggered this action.
     * @return A map representing the output of the plugin execution.
     */
    @Transactional
    public Map<String, Object> executeAction(String pluginName, Map<String, Object> parametersMap, UUID triggerringTaskId) {
        logger.info("Attempting to execute action for plugin: {} with parameters: {}", pluginName, parametersMap);

        Optional<PluginEntity> pluginEntityOpt = pluginRepository.findByName(pluginName);
        if (pluginEntityOpt.isEmpty() || !pluginEntityOpt.get().isEnabled()) {
            logger.error("Plugin '{}' not found or is disabled.", pluginName);
            throw new IllegalArgumentException("Plugin '" + pluginName + "' not found or is disabled.");
        }
        PluginEntity pluginEntity = pluginEntityOpt.get();

        ActionEntity actionEntity = new ActionEntity();
        actionEntity.setPlugin(pluginEntity);
        actionEntity.setTask(triggerringTaskId != null ? applicationContext.getBean(TaskService.class).getTask(triggerringTaskId).orElse(null) : null);
        try {
            actionEntity.setParameters(objectMapper.writeValueAsString(parametersMap));
        } catch (JsonProcessingException e) {
            logger.error("Error serializing parameters for action: {}", e.getMessage(), e);
            actionEntity.setParameters("{\"error\":\"Serialization failed\"}");
        }
        actionEntity.setStatus(ActionStatus.RUNNING);
        actionEntity.setStartTime(OffsetDateTime.now());
        ActionEntity savedActionEntity = actionRepository.save(actionEntity);

        try {
            PluginInterface pluginInstance = getPluginInstance(pluginEntity);
            // TODO: Validate parameters against pluginEntity.getInputSchema() before execution

            Map<String, Object> result = pluginInstance.execute(parametersMap);

            savedActionEntity.setStatus(ActionStatus.SUCCESS);
            savedActionEntity.setOutput(objectMapper.writeValueAsString(result));
            logger.info("Plugin '{}' executed successfully. Result: {}", pluginName, result);
            return result;

        } catch (Exception e) {
            logger.error("Error executing plugin '{}': {}", pluginName, e.getMessage(), e);
            savedActionEntity.setStatus(ActionStatus.FAILED);
            savedActionEntity.setExecutionLog(e.getMessage());
            // Rethrow or return error structure
            throw new RuntimeException("Plugin execution failed: " + e.getMessage(), e);
        } finally {
            savedActionEntity.setEndTime(OffsetDateTime.now());
            actionRepository.save(savedActionEntity);
        }
    }

    private PluginInterface getPluginInstance(PluginEntity pluginEntity) {
        String entryPoint = pluginEntity.getEntryPoint();
        String entryPointType = pluginEntity.getEntryPointType();

        try {
            if ("spring_bean".equalsIgnoreCase(entryPointType)) {
                return applicationContext.getBean(entryPoint, PluginInterface.class);
            } else if ("java_class".equalsIgnoreCase(entryPointType)) {
                Class<?> pluginClass = Class.forName(entryPoint);
                if (PluginInterface.class.isAssignableFrom(pluginClass)) {
                    // If the class is a Spring @Component, get it from context,
                    // otherwise, try to create a new instance.
                    try {
                        return (PluginInterface) applicationContext.getBean(pluginClass);
                    } catch (Exception e) {
                        logger.warn("Plugin class {} not found as a Spring bean, attempting direct instantiation.", entryPoint);
                        return (PluginInterface) pluginClass.getDeclaredConstructor().newInstance();
                    }
                } else {
                    throw new IllegalArgumentException("Class " + entryPoint + " does not implement PluginInterface.");
                }
            } else {
                throw new IllegalArgumentException("Unsupported entry point type: " + entryPointType);
            }
        } catch (Exception e) {
            logger.error("Could not get instance for plugin {}: {}", pluginEntity.getName(), e.getMessage(), e);
            throw new RuntimeException("Failed to load plugin " + pluginEntity.getName(), e);
        }
    }

    /**
     * Registers a plugin by saving its definition to the database.
     * This is useful for dynamic plugin registration or updates.
     * In many cases, plugins might be auto-discovered (e.g., from classpath)
     * and their definitions populated into the DB at startup.
     */
    @Transactional
    public PluginEntity registerPlugin(PluginSchema schema, String entryPoint, String entryPointType, boolean isEnabled) {
        Optional<PluginEntity> existingPlugin = pluginRepository.findByName(schema.getPluginName());
        PluginEntity pluginEntity = existingPlugin.orElseGet(PluginEntity::new);

        pluginEntity.setName(schema.getPluginName());
        pluginEntity.setDescription(schema.getDescription());
        try {
            // Assuming PluginParameter has fields that can be serialized to JSON for schema
            pluginEntity.setInputSchema(objectMapper.writeValueAsString(schema.getInputParameters()));
            pluginEntity.setOutputSchema(objectMapper.writeValueAsString(schema.getOutputParameters()));
        } catch (JsonProcessingException e) {
            logger.error("Error serializing plugin schema to JSON for plugin {}: {}", schema.getPluginName(), e.getMessage(), e);
            throw new RuntimeException("Could not serialize plugin schema", e);
        }
        pluginEntity.setEntryPoint(entryPoint);
        pluginEntity.setEntryPointType(entryPointType); // "spring_bean" or "java_class"
        pluginEntity.setEnabled(isEnabled);
        // Version can be part of PluginSchema or managed separately
        pluginEntity.setVersion("1.0.0"); // Placeholder

        logger.info("Registering plugin: {}", schema.getPluginName());
        return pluginRepository.save(pluginEntity);
    }

    @Transactional(readOnly = true)
    public List<PluginSchema> listAvailablePlugins() {
        // Assuming PluginRepository has a method: List<PluginEntity> findAllByIsEnabledTrue();
        // If not, fetch all and filter: pluginRepository.findAll().stream().filter(PluginEntity::isEnabled) ...
        return pluginRepository.findAll().stream().filter(PluginEntity::isEnabled)
            .map(pluginEntity -> {
                try {
                    List<PluginParameter> inputParams = objectMapper.readValue(pluginEntity.getInputSchema(), new TypeReference<List<PluginParameter>>() {});
                    List<PluginParameter> outputParams = objectMapper.readValue(pluginEntity.getOutputSchema(), new TypeReference<List<PluginParameter>>() {});
                    return new PluginSchema(pluginEntity.getName(), pluginEntity.getDescription(), inputParams, outputParams);
                } catch (JsonProcessingException e) {
                    logger.error("Error deserializing schema for plugin {}: {}", pluginEntity.getName(), e.getMessage(), e);
                    return new PluginSchema(pluginEntity.getName(), "Error: Could not parse schema", List.of(), List.of());
                }
            })
            .collect(Collectors.toList());
    }
}
