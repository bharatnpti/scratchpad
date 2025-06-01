package com.example.llmagentsystem.repository;

import com.example.llmagentsystem.model.entity.ConversationEntity;
import com.example.llmagentsystem.model.entity.MessageEntity;
import com.example.llmagentsystem.model.entity.UserEntity;
import com.example.llmagentsystem.model.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class MessageRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository; // For creating UserEntity dependencies

    @Autowired
    private ConversationRepository conversationRepository; // For creating ConversationEntity dependencies

    private UserEntity testUser;
    private UserEntity agentUser;
    private ConversationEntity testConversation;

    @BeforeEach
    void setUp() {
        UserEntity user = new UserEntity();
        user.setPlatformId("msgTestUser");
        user.setSourcePlatform("test");
        user.setEmail("msgTestUser@example.com");
        testUser = userRepository.save(user);

        UserEntity agent = new UserEntity();
        agent.setPlatformId("msgTestAgent");
        agent.setSourcePlatform("internal");
        agent.setEmail("msgTestAgent@example.com");
        agentUser = userRepository.save(agent);

        ConversationEntity conversation = new ConversationEntity();
        conversation.setPlatformConversationId("msgTestConvo1");
        conversation.setSourcePlatform("test");
        testConversation = conversationRepository.save(conversation);

        entityManager.flush();
    }

    @Test
    void whenSaveMessage_thenFindById_returnsMessage() {
        MessageEntity newMessage = new MessageEntity();
        newMessage.setConversation(testConversation);
        newMessage.setUser(testUser);
        newMessage.setContent("Hello from test user!");
        newMessage.setRole(UserRole.USER);
        newMessage.setPlatformMessageId("platformMsg1");
        newMessage.setTimestamp(OffsetDateTime.now());

        MessageEntity savedMessage = messageRepository.save(newMessage);
        entityManager.flush();
        entityManager.clear();

        Optional<MessageEntity> foundOpt = messageRepository.findById(savedMessage.getMessageId());

        assertThat(foundOpt).isPresent();
        MessageEntity found = foundOpt.get();
        assertThat(found.getContent()).isEqualTo("Hello from test user!");
        assertThat(found.getRole()).isEqualTo(UserRole.USER);
        assertThat(found.getUser().getUserId()).isEqualTo(testUser.getUserId());
        assertThat(found.getConversation().getConversationId()).isEqualTo(testConversation.getConversationId());
        assertThat(found.getMessageId()).isNotNull();
    }

    @Test
    void findByConversationConversationIdOrderByTimestampDesc_withPageable_returnsPaginatedAndSortedMessages() {
        OffsetDateTime now = OffsetDateTime.now();

        MessageEntity msg1 = new MessageEntity(); // Oldest
        msg1.setConversation(testConversation);
        msg1.setUser(testUser);
        msg1.setContent("Message 1");
        msg1.setRole(UserRole.USER);
        msg1.setTimestamp(now.minusMinutes(10));
        entityManager.persist(msg1);

        MessageEntity msg2 = new MessageEntity(); // Middle
        msg2.setConversation(testConversation);
        msg2.setUser(agentUser);
        msg2.setContent("Message 2 - Agent");
        msg2.setRole(UserRole.AGENT);
        msg2.setTimestamp(now.minusMinutes(5));
        entityManager.persist(msg2);

        MessageEntity msg3 = new MessageEntity(); // Newest
        msg3.setConversation(testConversation);
        msg3.setUser(testUser);
        msg3.setContent("Message 3");
        msg3.setRole(UserRole.USER);
        msg3.setTimestamp(now);
        entityManager.persist(msg3);

        entityManager.flush();

        // Test fetching the first page (most recent 2 messages)
        Pageable firstPage = PageRequest.of(0, 2, Sort.by("timestamp").descending());
        List<MessageEntity> foundMessagesPage1 = messageRepository.findByConversationConversationIdOrderByTimestampDesc(testConversation.getConversationId(), firstPage);

        assertThat(foundMessagesPage1).hasSize(2);
        assertThat(foundMessagesPage1.get(0).getContent()).isEqualTo("Message 3"); // Newest
        assertThat(foundMessagesPage1.get(1).getContent()).isEqualTo("Message 2 - Agent");

        // Test fetching the second page (oldest 1 message)
        Pageable secondPage = PageRequest.of(1, 2, Sort.by("timestamp").descending());
        List<MessageEntity> foundMessagesPage2 = messageRepository.findByConversationConversationIdOrderByTimestampDesc(testConversation.getConversationId(), secondPage);

        assertThat(foundMessagesPage2).hasSize(1);
        assertThat(foundMessagesPage2.get(0).getContent()).isEqualTo("Message 1"); // Oldest
    }

    @Test
    void findByConversationConversationIdOrderByTimestampDesc_withoutPageable_returnsAllSortedMessages() {
        OffsetDateTime now = OffsetDateTime.now();
        MessageEntity msg1 = new MessageEntity(); msg1.setConversation(testConversation); msg1.setUser(testUser); msg1.setContent("Oldest"); msg1.setRole(UserRole.USER); msg1.setTimestamp(now.minusHours(1));
        MessageEntity msg2 = new MessageEntity(); msg2.setConversation(testConversation); msg2.setUser(agentUser); msg2.setContent("Newer"); msg2.setRole(UserRole.AGENT); msg2.setTimestamp(now);
        entityManager.persist(msg1);
        entityManager.persist(msg2);
        entityManager.flush();

        List<MessageEntity> foundMessages = messageRepository.findByConversationConversationIdOrderByTimestampDesc(testConversation.getConversationId());

        assertThat(foundMessages).hasSize(2);
        assertThat(foundMessages.get(0).getContent()).isEqualTo("Newer"); // Newest first
        assertThat(foundMessages.get(1).getContent()).isEqualTo("Oldest");
    }

    @Test
    void whenMessageEmbeddingIdIsSet_itIsPersisted() {
        MessageEntity message = new MessageEntity();
        message.setConversation(testConversation);
        message.setUser(testUser);
        message.setContent("Message with embedding");
        message.setRole(UserRole.USER);
        message.setTimestamp(OffsetDateTime.now());

        String embeddingId = "embedding_vector_123";
        message.setEmbeddingId(embeddingId);

        MessageEntity savedMessage = messageRepository.save(message);
        entityManager.flush();
        entityManager.clear();

        Optional<MessageEntity> foundOpt = messageRepository.findById(savedMessage.getMessageId());
        assertThat(foundOpt).isPresent();
        assertThat(foundOpt.get().getEmbeddingId()).isEqualTo(embeddingId);
    }
}
