package com.example.llmagentsystem.service;

import com.example.llmagentsystem.model.entity.MessageEntity;
import com.example.llmagentsystem.repository.MessageRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

// Define a simple record for retrieved context
record RetrievedContextItem(UUID messageId, String content, String role, Map<String, Object> metadata) {}

@Service
public class MemoryService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryService.class);

    private final VectorStore vectorStore;
    private final EmbeddingClient embeddingClient; // Used if embeddings need to be generated explicitly before Document creation for some VectorStore impls
    private final MessageRepository messageRepository;

    @Autowired
    public MemoryService(VectorStore vectorStore, EmbeddingClient embeddingClient, MessageRepository messageRepository) {
        this.vectorStore = vectorStore;
        this.embeddingClient = embeddingClient;
        this.messageRepository = messageRepository;
    }

    /**
     * Stores a message by saving its metadata to the relational DB and its content + embedding to Qdrant.
     *
     * @param messageEntity The MessageEntity to store. This entity should be saved via MessageRepository by the caller
     *                      or this method should handle saving it. For transactional consistency, it's often better
     *                      if the caller saves the MessageEntity first, then passes it here.
     *                      This example assumes messageEntity is already persisted or will be by the caller.
     */
    @Transactional // Potentially part of a larger transaction managed by the caller
    public void storeMessageMemory(MessageEntity messageEntity) {
        if (messageEntity == null || messageEntity.getMessageId() == null || messageEntity.getContent() == null) {
            logger.warn("Attempted to store null or incomplete message entity in memory.");
            return;
        }
        logger.debug("Storing message memory for message ID: {}", messageEntity.getMessageId());

        try {
            // Metadata for the document in the vector store
            // Ensure these align with what LLD specified for VectorDB metadata
            Map<String, Object> metadata = Map.of(
                "message_id", messageEntity.getMessageId().toString(),
                "conversation_id", messageEntity.getConversation().getConversationId().toString(),
                "user_id", messageEntity.getUser().getUserId().toString(),
                // "thread_id" - This might be the same as platform_conversation_id or a sub-thread.
                // For now, using platform_conversation_id from ConversationEntity if available.
                "thread_id", messageEntity.getConversation().getPlatformConversationId() != null ? messageEntity.getConversation().getPlatformConversationId() : "N/A",
                "timestamp", messageEntity.getTimestamp().toEpochSecond(ZoneOffset.UTC), // Store as epoch seconds
                "role", messageEntity.getRole().name(),
                "text_preview", messageEntity.getContent().substring(0, Math.min(messageEntity.getContent().length(), 100)) // First 100 chars
            );

            // The Document ID for vectorStore can be the MessageEntity's UUID.
            // Spring AI's Document class automatically handles embedding generation if an EmbeddingClient is configured
            // with the VectorStore implementation (like QdrantVectorStore).
            // The content of the Document is what gets embedded.
            Document document = new Document(messageEntity.getMessageId().toString(), messageEntity.getContent(), metadata);

            vectorStore.add(List.of(document));
            logger.info("Successfully stored message {} embedding in vector store.", messageEntity.getMessageId());

            // Update MessageEntity with embeddingId (can be the document ID or a specific ID from vector store)
            // For simplicity, we are using the messageId as the document ID.
            // If vectorStore.add() returned specific embedding IDs, you'd use those.
            messageEntity.setEmbeddingId(messageEntity.getMessageId().toString()); // Mark that it has an embedding
            messageRepository.save(messageEntity); // Persist the embeddingId reference

        } catch (Exception e) {
            logger.error("Error storing message {} in vector store: {}", messageEntity.getMessageId(), e.getMessage(), e);
            // Optional: Add compensating transaction logic if needed, though usually logging is sufficient here.
        }
    }

    /**
     * Retrieves relevant context from memory based on semantic similarity to the query text.
     *
     * @param queryText The text to search for relevant context.
     * @param conversationId The ID of the current conversation (to potentially scope search or boost results).
     * @param topK The number of relevant items to retrieve.
     * @return A list of RetrievedContextItem objects.
     */
    @Transactional(readOnly = true)
    public List<RetrievedContextItem> retrieveRelevantContext(String queryText, UUID conversationId, int topK) {
        logger.debug("Retrieving relevant context for query: '{}' in conversation: {}, topK: {}", queryText, conversationId, topK);

        try {
            // SearchRequest allows specifying similarity search parameters.
            // The queryText itself is embedded by the VectorStore implementation.
            SearchRequest request = SearchRequest.query(queryText).withTopK(topK);

            // Optionally, one could add filters to the SearchRequest if supported by the VectorStore
            // e.g., .withFilterExpression("'conversation_id' == '" + conversationId.toString() + "'")
            // Filter syntax is specific to the VectorStore implementation.
            // For Qdrant, this would involve constructing Qdrant-specific filter conditions.
            // Spring AI's metadata filtering capabilities are evolving.

            List<Document> similarDocuments = vectorStore.similaritySearch(request);
            logger.debug("Found {} similar documents in vector store.", similarDocuments.size());

            List<UUID> messageIds = similarDocuments.stream()
                .map(doc -> {
                    try {
                        // The ID of the document should be our MessageEntity UUID string
                        return UUID.fromString(doc.getId());
                    } catch (IllegalArgumentException e) {
                        logger.warn("Found document with non-UUID ID: {}", doc.getId());
                        return null;
                    }
                })
                .filter(uuid -> uuid != null)
                .collect(Collectors.toList());

            if (messageIds.isEmpty()) {
                return List.of();
            }

            // Fetch full message details from relational DB
            List<MessageEntity> messages = messageRepository.findAllById(messageIds);

            return messages.stream()
                .map(msg -> new RetrievedContextItem(
                    msg.getMessageId(),
                    msg.getContent(),
                    msg.getRole().name(),
                    // Reconstruct metadata if needed, or fetch from Document's metadata
                    // For simplicity, not re-fetching all doc metadata here.
                    Map.of(
                        "timestamp", msg.getTimestamp().toString(),
                        "user_id", msg.getUser().getUserId().toString()
                        // Add other relevant metadata from MessageEntity as needed
                    )
                ))
                .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Error retrieving relevant context from vector store: {}", e.getMessage(), e);
            return List.of();
        }
    }
     /**
     * Deletes a message's memory representation from the vector store.
     *
     * @param messageId The UUID of the message to delete.
     * @return true if deletion was successful or document not found, false otherwise.
     */
    public boolean deleteMessageMemory(UUID messageId) {
        if (messageId == null) {
            logger.warn("Attempted to delete memory for a null messageId.");
            return false;
        }
        logger.debug("Deleting message memory for ID: {}", messageId);
        try {
            // VectorStore delete method typically takes a list of document IDs.
            // The ID here should match the ID used when the document was added.
            Optional<Boolean> result = vectorStore.delete(List.of(messageId.toString()));

            if (result.isPresent() && result.get()) {
                logger.info("Successfully deleted document with ID '{}' from vector store.", messageId);
                return true;
            } else if (result.isPresent() && !result.get()){
                logger.warn("Document with ID '{}' not found in vector store for deletion or delete failed.", messageId);
                return true; // Treat not found also as "successful" from caller's perspective (it's gone)
            } else {
                 logger.warn("Vector store delete operation did not return a clear success/failure for ID '{}'.", messageId);
                return false; // Ambiguous result
            }
        } catch (UnsupportedOperationException e) {
            logger.error("The configured vector store does not support delete operations: {}", e.getMessage());
            return false;
        }
        catch (Exception e) {
            logger.error("Error deleting document with ID '{}' from vector store: {}", messageId, e.getMessage(), e);
            return false;
        }
    }
}
