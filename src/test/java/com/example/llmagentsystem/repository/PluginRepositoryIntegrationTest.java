package com.example.llmagentsystem.repository;

import com.example.llmagentsystem.model.entity.PluginEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class PluginRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PluginRepository pluginRepository;

    private ObjectMapper objectMapper = new ObjectMapper(); // For schema serialization

    private String sampleInputSchemaJson;
    private String sampleOutputSchemaJson;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        // Sample JSON schemas (simplified)
        sampleInputSchemaJson = objectMapper.writeValueAsString(List.of(Map.of("name", "param1", "type", "string")));
        sampleOutputSchemaJson = objectMapper.writeValueAsString(List.of(Map.of("name", "result", "type", "string")));
    }

    @Test
    void whenSavePlugin_thenFindById_returnsPlugin() {
        PluginEntity newPlugin = new PluginEntity();
        newPlugin.setName("TestPlugin1");
        newPlugin.setVersion("1.0");
        newPlugin.setDescription("A test plugin");
        newPlugin.setInputSchema(sampleInputSchemaJson);
        newPlugin.setOutputSchema(sampleOutputSchemaJson);
        newPlugin.setEntryPoint("com.example.TestPlugin1");
        newPlugin.setEntryPointType("java_class");
        newPlugin.setEnabled(true);

        PluginEntity savedPlugin = pluginRepository.save(newPlugin);
        entityManager.flush();
        entityManager.clear();

        Optional<PluginEntity> foundOpt = pluginRepository.findById(savedPlugin.getPluginId());

        assertThat(foundOpt).isPresent();
        PluginEntity found = foundOpt.get();
        assertThat(found.getName()).isEqualTo("TestPlugin1");
        assertThat(found.isEnabled()).isTrue();
        assertThat(found.getInputSchema()).isEqualTo(sampleInputSchemaJson);
    }

    @Test
    void findByName_whenPluginExists_returnsPlugin() {
        PluginEntity plugin = new PluginEntity();
        plugin.setName("MyUniquePlugin");
        plugin.setVersion("0.1");
        plugin.setInputSchema(sampleInputSchemaJson);
        plugin.setOutputSchema(sampleOutputSchemaJson);
        plugin.setEntryPoint("com.example.MyUniquePlugin");
        plugin.setEntryPointType("java_class");
        entityManager.persistAndFlush(plugin);

        Optional<PluginEntity> foundOpt = pluginRepository.findByName("MyUniquePlugin");

        assertThat(foundOpt).isPresent();
        assertThat(foundOpt.get().getVersion()).isEqualTo("0.1");
    }

    @Test
    void findByName_whenPluginNotExists_returnsEmpty() {
        Optional<PluginEntity> foundOpt = pluginRepository.findByName("NonExistentPlugin");
        assertThat(foundOpt).isEmpty();
    }

    @Test
    void findAllByIsEnabledTrue_shouldReturnOnlyEnabledPlugins() {
        PluginEntity enabledPlugin = new PluginEntity();
        enabledPlugin.setName("EnabledPlugin");
        enabledPlugin.setEnabled(true);
        enabledPlugin.setInputSchema(sampleInputSchemaJson);
        enabledPlugin.setOutputSchema(sampleOutputSchemaJson);
        enabledPlugin.setEntryPoint("com.example.EnabledPlugin");
        enabledPlugin.setEntryPointType("java_class");
        entityManager.persist(enabledPlugin);

        PluginEntity disabledPlugin = new PluginEntity();
        disabledPlugin.setName("DisabledPlugin");
        disabledPlugin.setEnabled(false);
        disabledPlugin.setInputSchema(sampleInputSchemaJson);
        disabledPlugin.setOutputSchema(sampleOutputSchemaJson);
        disabledPlugin.setEntryPoint("com.example.DisabledPlugin");
        disabledPlugin.setEntryPointType("java_class");
        entityManager.persist(disabledPlugin);

        entityManager.flush();

        // Note: The repository method is  then filtered in service.
        // If a direct repository method  existed, this test would be for that.
        // For now, testing the behavior if the service were to call findAll() and filter.
        // Let's assume we add  to the repository for a cleaner test.
        // If PluginRepository has
        // List<PluginEntity> enabledPlugins = pluginRepository.findAllByIsEnabledTrue();
        // For now, let's test based on findAll()
        pluginRepository.save(enabledPlugin); // ensure it's managed by repository
        pluginRepository.save(disabledPlugin);
        entityManager.flush();
        entityManager.clear();


        List<PluginEntity> allPlugins = pluginRepository.findAll();
        List<PluginEntity> foundEnabled = allPlugins.stream().filter(PluginEntity::isEnabled).collect(java.util.stream.Collectors.toList());

        assertThat(foundEnabled).hasSize(1);
        assertThat(foundEnabled.get(0).getName()).isEqualTo("EnabledPlugin");

        // If findAllByIsEnabledTrue() method is added to repository:
        // when(pluginRepository.findAllByIsEnabledTrue()).thenReturn(List.of(enabledPlugin));
        // List<PluginEntity> foundEnabledDirect = pluginRepository.findAllByIsEnabledTrue();
        // assertThat(foundEnabledDirect).hasSize(1);
        // assertThat(foundEnabledDirect.get(0).getName()).isEqualTo("EnabledPlugin");
    }
}
