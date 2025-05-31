package com.example.llmagentsystem.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
// import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.Role; // Not used directly in this version
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
// import java.util.List; // Not used directly in this version
// import java.util.stream.Collectors; // Not used directly in this version

// Placeholder for a more structured response for understanding text
// In a real scenario, this might map to a Pojo with fields for intent, entities, etc.
// Spring AI's function calling or output parsers would populate this.
record UnderstoodText(String intent, Map<String, Object> entities, String originalText) {}

@Service
public class NlpService {

    private static final Logger logger = LoggerFactory.getLogger(NlpService.class);

    private final ChatClient chatClient;

    @Autowired
    public NlpService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Understands the given text to extract intent and entities.
     * This is a simplified example. Real implementation would use more robust
     * prompting, function calling, or structured output parsing.
     *
     * @param text The input text from the user.
     * @return An UnderstoodText object.
     */
    public UnderstoodText understandText(String text) {
        logger.debug("Understanding text: '{}'", text);
        // Example: Simple prompt to ask the LLM to classify intent and extract entities
        // This would need to be much more sophisticated for reliable results.
        // Consider using Spring AI's OutputParser features for structured output.
        String promptString = """
            Analyze the following text and identify the primary intent and any relevant entities.
            Provide the intent as a single keyword (e.g., CREATE_TASK, GET_WEATHER, SEND_MESSAGE).
            Provide entities as key-value pairs.
            If intent is unclear, respond with INTENT_UNCLEAR.

            Example:
            Text: "Remind me to buy milk tomorrow at 5pm"
            Response: Intent: CREATE_TASK, Entities: {"item": "milk", "time": "tomorrow at 5pm"}

            Text: "{inputText}"
            Response:
            """;
        PromptTemplate promptTemplate = new PromptTemplate(promptString);
        Prompt prompt = promptTemplate.create(Map.of("inputText", text));

        try {
            ChatResponse response = chatClient.call(prompt);
            String llmResponse = response.getResult().getOutput().getContent();
            logger.debug("LLM response for understanding: {}", llmResponse);

            // Basic parsing of the LLM response (highly simplified)
            // TODO: Implement robust parsing for intent and entities
            String intent = "PARSED_INTENT_STUB"; // Placeholder
            Map<String, Object> entities = Map.of("detail", llmResponse); // Placeholder

            if (llmResponse.contains("Intent: CREATE_TASK")) { // Example crude parsing
                intent = "CREATE_TASK";
            } else if (llmResponse.contains("Intent: GET_WEATHER")) {
                intent = "GET_WEATHER";
            }
            // ... more sophisticated parsing needed here

            return new UnderstoodText(intent, entities, text);
        } catch (Exception e) {
            logger.error("Error calling LLM for understanding text: {}", e.getMessage(), e);
            return new UnderstoodText("ERROR_UNDERSTANDING", Map.of("error", e.getMessage()), text);
        }
    }

    /**
     * Generates text based on a given prompt.
     *
     * @param userPrompt The prompt to generate text from.
     * @return The generated text.
     */
    public String generateText(String userPrompt) {
        logger.debug("Generating text for prompt: '{}'", userPrompt);
        Prompt prompt = new Prompt(userPrompt);
        try {
            ChatResponse response = chatClient.call(prompt);
            return response.getResult().getOutput().getContent();
        } catch (Exception e) {
            logger.error("Error calling LLM for text generation: {}", e.getMessage(), e);
            return "Error: Could not generate text due to: " + e.getMessage();
        }
    }

    /**
     * Summarizes the given text.
     *
     * @param textToSummarize The text to be summarized.
     * @return The summarized text.
     */
    public String summarizeText(String textToSummarize) {
        logger.debug("Summarizing text of length: {}", textToSummarize.length());
        String promptString = "Please summarize the following text concisely: \n\n{text}"; // Escaped \n for shell
        PromptTemplate promptTemplate = new PromptTemplate(promptString);
        Prompt prompt = promptTemplate.create(Map.of("text", textToSummarize));

        try {
            // Example of using OpenAiChatOptions if needed for specific models or parameters
            // OpenAiChatOptions options = OpenAiChatOptions.builder()
            //         .withModel("gpt-3.5-turbo") // Or your preferred model for summarization
            //         .withTemperature(0.5f)
            //         .build();
            // Prompt promptWithOptions = new Prompt(prompt.getContents(), options);
            // ChatResponse response = chatClient.call(promptWithOptions);

            ChatResponse response = chatClient.call(prompt);
            return response.getResult().getOutput().getContent();
        } catch (Exception e) {
            logger.error("Error calling LLM for summarization: {}", e.getMessage(), e);
            return "Error: Could not summarize text due to: " + e.getMessage();
        }
    }

    /**
     * Generates embeddings for a given text.
     * This method might be better placed in MemoryService if using EmbeddingClient directly,
     * but NlpService could also expose it if it coordinates various NLP tasks.
     * For now, assuming EmbeddingClient is used elsewhere (e.g. MemoryService).
     * If NlpService needs to provide embeddings itself:
     *
     * import org.springframework.ai.embedding.EmbeddingClient;
     * private final EmbeddingClient embeddingClient;
     *
     * public List<Double> embedText(String text) {
     *     logger.debug("Embedding text: '{}'", text);
     *     try {
     *         return embeddingClient.embed(text);
     *     } catch (Exception e) {
     *         logger.error("Error generating embedding: {}", e.getMessage(), e);
     *         return List.of();
     *     }
     * }
     */
}
