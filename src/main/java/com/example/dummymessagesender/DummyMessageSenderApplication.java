package java.com.example.dummymessagesender;

import com.example.dummymessagesender.dto.ProcessTextRequest;
import com.example.dummymessagesender.dto.AgentResponse;
import com.example.dummymessagesender.service.ConsoleInputService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class DummyMessageSenderApplication {

    private static final Logger log = LoggerFactory.getLogger(DummyMessageSenderApplication.class);
    // Make sure the main LLM Agent application is running on localhost:8080
    private final String targetUrl = "http://localhost:8080/api/v1/chat/process";

    public static void main(String[] args) {
        // For a CLI app, it's better to manage the context explicitly for shutdown
        ConfigurableApplicationContext ctx = SpringApplication.run(DummyMessageSenderApplication.class, args);
        // The CommandLineRunner will execute, and then we can close the context.
        // However, the loop in CommandLineRunner needs to finish first.
        // The application will wait for the CommandLineRunner's thread to complete.
        // Once the loop finishes (user types 'exit'), the run method completes,
        // and then Spring Boot will shut down.
        // Explicit ctx.close() might not be needed if the CommandLineRunner is the only foreground activity.
    }

    @Bean
    public WebClient webClient() {
        // Setting default headers for all requests from this WebClient instance
        return WebClient.builder()
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    public CommandLineRunner run(WebClient webClient, ConsoleInputService consoleInputService) {
        return args -> {
            log.info("Dummy Message Sender CLI started.");
            log.info("Targeting LLM Agent at: {}", targetUrl);

            // Loop to allow multiple messages
            while (true) {
                ProcessTextRequest request = consoleInputService.gatherInputLoop();

                if (request == null) { // User typed 'exit'
                    log.info("Exiting application.");
                    break;
                }

                log.info("Sending request to {}: User='{}', Conv='{}', Platform='{}', Text='{}'",
                    targetUrl, request.getUserPlatformId(), request.getConversationPlatformId(),
                    request.getSourcePlatform(), request.getTextInput());

                try {
                    // WebClient call is non-blocking. We need to block for a CLI app to see the response.
                    AgentResponse response = webClient.post()
                        .uri(targetUrl) // Use full URI here as baseUrl is not set globally for this client
                        .body(Mono.just(request), ProcessTextRequest.class)
                        .retrieve()
                        .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Error from server ({}): {}", clientResponse.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException("Server error: " + clientResponse.statusCode() + " - " + errorBody));
                                }))
                        .bodyToMono(AgentResponse.class)
                        .block(); // Block to wait for the response in CLI

                    if (response != null) {
                        log.info("Received response: Agent says='{}', User='{}', Conv='{}'",
                                 response.getResponseText(), response.getUserPlatformId(), response.getConversationPlatformId());
                    } else {
                        log.warn("No response received from server.");
                    }

                } catch (Exception e) {
                    log.error("Error sending request or processing response: {}", e.getMessage(), e);
                }
            }
            log.info("Dummy Message Sender finished. Application will now exit.");
        };
    }
}
