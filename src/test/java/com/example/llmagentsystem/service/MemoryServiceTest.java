package com.example.llmagentsystem.service;

import com.example.llmagentsystem.model.entity.ConversationEntity;
import com.example.llmagentsystem.model.entity.MessageEntity;
import com.example.llmagentsystem.model.entity.UserEntity;
import com.example.llmagentsystem.model.enums.UserRole;
import com.example.llmagentsystem.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient; // Not directly used if VectorStore handles embedding
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoryServiceTest {

    @Mock
    private VectorStore mockVectorStore;

    @Mock
    private EmbeddingClient mockEmbeddingClient; // May not be directly used if VectorStore implies it

    @Mock
    private MessageRepository mockMessageRepository;

    @InjectMocks
    private MemoryService memoryService;

    private UserEntity testUser;
    private ConversationEntity testConversation;
    private MessageEntity testMessage;

    @BeforeEach
    void setUp() {
        testUser = new UserEntity();
        testUser.setUserId(UUID.randomUUID());

        testConversation = new ConversationEntity();
        testConversation.setConversationId(UUID.randomUUID());
        testConversation.setPlatformConversationId("test_thread_123");

        testMessage = new MessageEntity();
        testMessage.setMessageId(UUID.randomUUID());
        testMessage.setContent("Hello, this is a test message.");
        testMessage.setUser(testUser);
        testMessage.setConversation(testConversation);
        testMessage.setRole(UserRole.USER);
        testMessage.setTimestamp(OffsetDateTime.now());
    }

    @Test
    void storeMessageMemory_shouldAddDocumentToVectorStoreAndUpdateMessageEntity() {
        // No specific return from vectorStore.add, so no 'when' needed for that unless checking interactions
        // Assume messageRepository.save returns the saved entity
        when(mockMessageRepository.save(any(MessageEntity.class))).thenReturn(testMessage);

        memoryService.storeMessageMemory(testMessage);

        ArgumentCaptor<List<Document>> documentListCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockVectorStore).add(documentListCaptor.capture());
        List<Document> capturedDocuments = documentListCaptor.getValue();
        assertThat(capturedDocuments).hasSize(1);
        Document storedDocument = capturedDocuments.get(0);
        assertThat(storedDocument.getId()).isEqualTo(testMessage.getMessageId().toString());
        assertThat(storedDocument.getContent()).isEqualTo(testMessage.getContent());

        Map<String, Object> metadata = storedDocument.getMetadata();
        assertThat(metadata.get("message_id")).isEqualTo(testMessage.getMessageId().toString());
        assertThat(metadata.get("conversation_id")).isEqualTo(testConversation.getConversationId().toString());
        assertThat(metadata.get("user_id")).isEqualTo(testUser.getUserId().toString());
        assertThat(metadata.get("thread_id")).isEqualTo("test_thread_123");
        assertThat(metadata.get("timestamp")).isEqualTo(testMessage.getTimestamp().toEpochSecond(ZoneOffset.UTC));
        assertThat(metadata.get("role")).isEqualTo(UserRole.USER.name());
        assertThat(metadata.get("text_preview")).isEqualTo(testMessage.getContent().substring(0, Math.min(testMessage.getContent().length(), 100)));


        // Verify that messageEntity was updated with embeddingId and saved
        ArgumentCaptor<MessageEntity> messageEntityCaptor = ArgumentCaptor.forClass(MessageEntity.class);
        verify(mockMessageRepository).save(messageEntityCaptor.capture());
        assertThat(messageEntityCaptor.getValue().getEmbeddingId()).isEqualTo(testMessage.getMessageId().toString());
    }

    @Test
    void storeMessageMemory_whenMessageIsNull_shouldNotInteractWithStores() {
        memoryService.storeMessageMemory(null);
        verifyNoInteractions(mockVectorStore);
        verifyNoInteractions(mockMessageRepository);
    }

    @Test
    void storeMessageMemory_whenVectorStoreThrowsException_shouldLogErrorAndNotUpdateMessage() {
        doThrow(new RuntimeException("VectorStore error")).when(mockVectorStore).add(anyList());

        memoryService.storeMessageMemory(testMessage);

        // messageRepository.save should not be called to set embeddingId if vector store fails
        verify(mockMessageRepository, never()).save(argThat(me -> me.getEmbeddingId() != null && me.getEmbeddingId().equals(testMessage.getMessageId().toString()) ));
        // This test assumes storeMessageMemory is responsible for the save call that sets embeddingId.
        // The current MemoryService.storeMessageMemory *does* call save to update embeddingId.
    }


    @Test
    void retrieveRelevantContext_shouldQueryVectorStoreAndFetchMessagesFromRepo() {
        String queryText = "test query";
        UUID conversationId = testConversation.getConversationId();
        int topK = 3;

        Document doc1 = new Document(testMessage.getMessageId().toString(), "Content 1", Map.of("role", "USER"));
        UUID anotherMessageId = UUID.randomUUID();
        Document doc2 = new Document(anotherMessageId.toString(), "Content 2", Map.of("role", "AGENT"));

        List<Document> similarDocs = List.of(doc1, doc2);
        when(mockVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(similarDocs);

        MessageEntity msg1FromDb = new MessageEntity();
        msg1FromDb.setMessageId(testMessage.getMessageId());
        msg1FromDb.setContent("Content 1");
        msg1FromDb.setRole(UserRole.USER);
        msg1FromDb.setTimestamp(OffsetDateTime.now());
        msg1FromDb.setUser(testUser);

        MessageEntity msg2FromDb = new MessageEntity();
        msg2FromDb.setMessageId(anotherMessageId);
        msg2FromDb.setContent("Content 2");
        msg2FromDb.setRole(UserRole.AGENT);
        msg2FromDb.setTimestamp(OffsetDateTime.now());
        // UserEntity agentUser = new UserEntity(); agentUser.setUserId(UUID.randomUUID()); // Simulate agent user
        msg2FromDb.setUser(testUser); // Or a specific agent user


        List<UUID> expectedIds = List.of(testMessage.getMessageId(), anotherMessageId);
        when(mockMessageRepository.findAllById(expectedIds)).thenReturn(List.of(msg1FromDb, msg2FromDb));

        List<RetrievedContextItem> results = memoryService.retrieveRelevantContext(queryText, conversationId, topK);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).messageId()).isEqualTo(testMessage.getMessageId());
        assertThat(results.get(0).content()).isEqualTo("Content 1");
        assertThat(results.get(1).messageId()).isEqualTo(anotherMessageId);
        assertThat(results.get(1).content()).isEqualTo("Content 2");

        ArgumentCaptor<SearchRequest> searchRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(mockVectorStore).similaritySearch(searchRequestCaptor.capture());
        assertThat(searchRequestCaptor.getValue().getQuery()).isEqualTo(queryText);
        assertThat(searchRequestCaptor.getValue().getTopK()).isEqualTo(topK);

        verify(mockMessageRepository).findAllById(expectedIds);
    }

    @Test
    void retrieveRelevantContext_whenVectorStoreReturnsEmpty_shouldReturnEmptyList() {
        String queryText = "test query";
        UUID conversationId = testConversation.getConversationId();
        int topK = 3;

        when(mockVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(Collections.emptyList());

        List<RetrievedContextItem> results = memoryService.retrieveRelevantContext(queryText, conversationId, topK);

        assertThat(results).isEmpty();
        verify(mockMessageRepository, never()).findAllById(anyList());
    }

    @Test
    void deleteMessageMemory_whenIdIsValid_shouldCallVectorStoreDelete() {
        UUID messageIdToDelete = UUID.randomUUID();
        when(mockVectorStore.delete(List.of(messageIdToDelete.toString()))).thenReturn(Optional.of(true));

        boolean result = memoryService.deleteMessageMemory(messageIdToDelete);

        assertThat(result).isTrue();
        verify(mockVectorStore).delete(List.of(messageIdToDelete.toString()));
    }

    @Test
    void deleteMessageMemory_whenIdIsNull_shouldReturnFalseAndNotCallDelete() {
        boolean result = memoryService.deleteMessageMemory(null);
        assertThat(result).isFalse();
        verify(mockVectorStore, never()).delete(anyList());
    }

    @Test
    void deleteMessageMemory_whenVectorStoreDeleteFails_shouldReturnTrue() { // As per current logic: not found or failed delete is "gone"
        UUID messageIdToDelete = UUID.randomUUID();
        when(mockVectorStore.delete(List.of(messageIdToDelete.toString()))).thenReturn(Optional.of(false));

        boolean result = memoryService.deleteMessageMemory(messageIdToDelete);
        assertThat(result).isTrue();
    }

    @Test
    void deleteMessageMemory_whenVectorStoreDeleteReturnsEmptyOptional_shouldReturnFalse() {
        UUID messageIdToDelete = UUID.randomUUID();
        when(mockVectorStore.delete(List.of(messageIdToDelete.toString()))).thenReturn(Optional.empty());

        boolean result = memoryService.deleteMessageMemory(messageIdToDelete);
        assertThat(result).isFalse(); // Ambiguous result from store
    }

    @Test
    void deleteMessageMemory_whenVectorStoreThrowsUnsupportedOperation_shouldReturnFalse() {
        UUID messageIdToDelete = UUID.randomUUID();
        when(mockVectorStore.delete(List.of(messageIdToDelete.toString()))).thenThrow(new UnsupportedOperationException("Delete not supported"));

        boolean result = memoryService.deleteMessageMemory(messageIdToDelete);
        assertThat(result).isFalse();
    }
}
