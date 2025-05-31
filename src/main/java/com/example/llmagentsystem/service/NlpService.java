package com.example.llmagentsystem.service;

import com.example.llmagentsystem.model.nlp.StructuredNlpResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.parser.BeanOutputParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Assuming RetrievedContextItem is accessible or defined similarly
// For simplicity, let's define a local record if not directly importing from MemoryService's scope
// For now, assume CoreOrchestrationService will pass a formatted string or a list of simple message strings.
// Let's refine this to accept a list of simple objects representing message history.

record ChatMessageHistoryItem(String role, String content) {}

// Pojo for BeanOutputParser (ensure it's accessible or defined here)
class IntentExtractionPojo {
    private String intent;
    private Map<String, Object> entities;
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public Map<String, Object> getEntities() { return entities; }
    public void setEntities(Map<String, Object> entities) { this.entities = entities; }
}

@Service
public class NlpService {

    private static final Logger logger = LoggerFactory.getLogger(NlpService.class);

    private final ChatClient chatClient;
    private final BeanOutputParser<IntentExtractionPojo> intentExtractionParser;

    @Autowired
    public NlpService(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.intentExtractionParser = new BeanOutputParser<>(IntentExtractionPojo.class);
    }

    /**
     * Formats a list of ChatMessageHistoryItem into a string for the prompt.
     */
    private String formatConversationHistory(List<ChatMessageHistoryItem> history) {
        if (history == null || history.isEmpty()) {
            return "No prior conversation history available for this interaction.";
        }
        return history.stream()
                .map(item -> item.role() + ": " + item.content())
                .collect(Collectors.joining("\n"));
    }

    /**
     * Understands the given text to extract intent and entities, considering conversation history.
     *
     * @param text The input text from the user.
     * @param conversationHistory A list of previous messages (role and content).
     * @return A StructuredNlpResult object.
     */
    public StructuredNlpResult understandText(String text, List<ChatMessageHistoryItem> conversationHistory) {
        logger.debug("Understanding text with history: '{}'", text);

        String formattedHistory = formatConversationHistory(conversationHistory);
        String formatInstructions = intentExtractionParser.getFormat();

        String promptString = """
            Given the following conversation history:
            --- START HISTORY ---
            {history}
            --- END HISTORY ---

            Analyze the following new text from the User:
            User: "{inputText}"

            Identify the primary intent and any relevant entities from the new text, considering the history for context.
            The primary intent should be one of the following keywords if applicable:
            CREATE_TASK, GET_WEATHER, CALCULATOR, ECHO, SEND_MESSAGE, UNKNOWN_INTENT.
            Extract entities as key-value pairs relevant to the intent.
            For CREATE_TASK, entities could include 'description', 'dueDate', 'assignee'.
            For CALCULATOR, entities could include 'operand1', 'operand2', 'operation'.
            For ECHO, the main text can be an entity like 'message'.

            {formatInstructions}
            """;

        PromptTemplate promptTemplate = new PromptTemplate(promptString);
        Prompt prompt = promptTemplate.create(Map.of(
            "history", formattedHistory,
            "inputText", text,
            "formatInstructions", formatInstructions
        ));

        logger.debug("Constructed prompt for LLM (understandText with history): {}", prompt.getContents());

        try {
            ChatResponse response = chatClient.call(prompt);
            String llmResponseContent = response.getResult().getOutput().getContent();
            logger.debug("Raw LLM response for structured understanding with history: {}", llmResponseContent);

            IntentExtractionPojo parsedPojo = intentExtractionParser.parse(llmResponseContent);

            String intent = parsedPojo.getIntent() != null ? parsedPojo.getIntent() : "UNKNOWN_INTENT";
            Map<String, Object> entities = parsedPojo.getEntities() != null ? parsedPojo.getEntities() : Map.of();

            return new StructuredNlpResult(intent, entities, text);

        } catch (Exception e) {
            logger.error("Error calling LLM or parsing response for structured understanding with history: {}", e.getMessage(), e);
            return new StructuredNlpResult("ERROR_NLP_PROCESSING", Map.of("error", e.getMessage()), text);
        }
    }

    /**
     * Generates text based on a given prompt, considering conversation history.
     *
     * @param userPromptText The current prompt/text from the user.
     * @param conversationHistory A list of previous messages (role and content).
     * @return The generated text.
     */
    public String generateText(String userPromptText, List<ChatMessageHistoryItem> conversationHistory) {
        logger.debug("Generating text for prompt with history: '{}'", userPromptText);

        String formattedHistory = formatConversationHistory(conversationHistory);

        // Construct a prompt that includes history and the new user message
        String fullPromptString = """
            Conversation History:
            {history}

            Current User Query:
            User: {currentUserPrompt}

            Agent Response:
            """; // The LLM will complete this.

        PromptTemplate promptTemplate = new PromptTemplate(fullPromptString);
        Prompt prompt = promptTemplate.create(Map.of(
            "history", formattedHistory,
            "currentUserPrompt", userPromptText
        ));

        logger.debug("Constructed prompt for LLM (generateText with history): {}", prompt.getContents());

        try {
            ChatResponse response = chatClient.call(prompt);
            return response.getResult().getOutput().getContent();
        } catch (Exception e) {
            logger.error("Error calling LLM for text generation with history: {}", e.getMessage(), e);
            return "Error: Could not generate text due to: " + e.getMessage();
        }
    }

    /**
     * Summarizes the given text. History might be less relevant here, or could be used to tailor summary style.
     * For now, not adding history to summarizeText, but it's an option.
     * @param textToSummarize The text to be summarized.
     * @return The summarized text.
     */
    public String summarizeText(String textToSummarize) {
        logger.debug("Summarizing text of length: {}", textToSummarize.length());
        String promptString = "Please summarize the following text concisely: \n\n{text}"; // Escaped \n
        PromptTemplate promptTemplate = new PromptTemplate(promptString);
        Prompt prompt = promptTemplate.create(Map.of("text", textToSummarize));

        try {
            ChatResponse response = chatClient.call(prompt);
            return response.getResult().getOutput().getContent();
        } catch (Exception e) {
            logger.error("Error calling LLM for summarization: {}", e.getMessage(), e);
            return "Error: Could not summarize text due to: " + e.getMessage();
        }
    }
}
