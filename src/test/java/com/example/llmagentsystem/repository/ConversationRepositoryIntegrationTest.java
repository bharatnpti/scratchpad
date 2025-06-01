package com.example.llmagentsystem.repository;

import com.example.llmagentsystem.model.entity.ConversationEntity;
import com.example.llmagentsystem.model.entity.UserEntity; // Needed if there are relations to User
import org.assertj.core.api.Assertions; // For Assertions.within
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test") // Ensure application-test.properties are used
class ConversationRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ConversationRepository conversationRepository;

    // Optional: If UserEntity is needed for any conversation relations or setup
    // @Autowired
    // private UserRepository userRepository;
    // private UserEntity testUser;

    @BeforeEach
    void setUp() {
        // Example: If conversations were linked to a user creating them
        // testUser = new UserEntity();
        // testUser.setPlatformId("convoTestUser");
        // testUser.setSourcePlatform("test");
        // userRepository.save(testUser);
        // entityManager.flush();
    }

    @Test
    void whenSaveConversation_thenFindById_returnsConversation() {
        ConversationEntity newConversation = new ConversationEntity();
        newConversation.setPlatformConversationId("platformConvoId123");
        newConversation.setSourcePlatform("testPlatform");
        newConversation.setType("dm");
        newConversation.setLastActivityTimestamp(OffsetDateTime.now());
        // newConversation.setCreatingUser(testUser); // If such a relation exists

        ConversationEntity savedConversation = conversationRepository.save(newConversation);
        entityManager.flush();
        entityManager.clear();

        Optional<ConversationEntity> foundOpt = conversationRepository.findById(savedConversation.getConversationId());

        assertThat(foundOpt).isPresent();
        ConversationEntity found = foundOpt.get();
        assertThat(found.getPlatformConversationId()).isEqualTo("platformConvoId123");
        assertThat(found.getType()).isEqualTo("dm");
        assertThat(found.getConversationId()).isNotNull();
    }

    @Test
    void findByPlatformConversationIdAndSourcePlatform_whenConversationExists_returnsConversation() {
        ConversationEntity convo = new ConversationEntity();
        convo.setPlatformConversationId("uniquePlatformId1");
        convo.setSourcePlatform("platformX");
        convo.setType("channel");
        entityManager.persistAndFlush(convo);

        Optional<ConversationEntity> foundOpt = conversationRepository.findByPlatformConversationIdAndSourcePlatform("uniquePlatformId1", "platformX");

        assertThat(foundOpt).isPresent();
        assertThat(foundOpt.get().getType()).isEqualTo("channel");
    }

    @Test
    void findByPlatformConversationIdAndSourcePlatform_whenConversationNotExists_returnsEmpty() {
        Optional<ConversationEntity> foundOpt = conversationRepository.findByPlatformConversationIdAndSourcePlatform("nonExistentConvo", "platformY");
        assertThat(foundOpt).isEmpty();
    }

    @Test
    void whenUpdateLastActivityTimestamp_thenTimestampIsUpdated() {
        ConversationEntity convo = new ConversationEntity();
        convo.setPlatformConversationId("convoToUpdate");
        convo.setSourcePlatform("updatePlatform");
        OffsetDateTime initialTimestamp = OffsetDateTime.now().minusDays(1);
        convo.setLastActivityTimestamp(initialTimestamp);
        ConversationEntity persistedConvo = entityManager.persistFlushFind(convo);

        OffsetDateTime newTimestamp = OffsetDateTime.now();
        // Ensure the new timestamp is distinctly different for a clear test
        if (!newTimestamp.isAfter(initialTimestamp)) {
            newTimestamp = initialTimestamp.plusHours(1);
        }

        persistedConvo.setLastActivityTimestamp(newTimestamp);
        conversationRepository.save(persistedConvo);
        entityManager.flush();
        entityManager.clear();

        Optional<ConversationEntity> updatedConvoOpt = conversationRepository.findById(persistedConvo.getConversationId());
        assertThat(updatedConvoOpt).isPresent();
        // Compare with tolerance due to potential precision differences with DB
        assertThat(updatedConvoOpt.get().getLastActivityTimestamp()).isCloseTo(newTimestamp, Assertions.within(1, ChronoUnit.SECONDS));
    }

    @Test
    void whenDeleteConversation_thenFindById_returnsEmpty() {
        ConversationEntity convo = new ConversationEntity();
        convo.setPlatformConversationId("convoToDelete");
        convo.setSourcePlatform("deletePlatform");
        ConversationEntity persistedConvo = entityManager.persistFlushFind(convo);
        UUID convoId = persistedConvo.getConversationId();

        conversationRepository.deleteById(convoId);
        entityManager.flush();
        entityManager.clear();

        Optional<ConversationEntity> foundOpt = conversationRepository.findById(convoId);
        assertThat(foundOpt).isEmpty();
    }
}
