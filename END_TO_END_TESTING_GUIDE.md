# End-to-End Testing Guide

This guide provides instructions on how to perform end-to-end testing for the LLM Agent System using the `dummy-message-sender` Java application.

## 1. Running the Main LLM Agent System

The main LLM Agent System is a Spring Boot application that processes user messages.

### Prerequisites:

1.  **Java 17 SDK:** Ensure you have Java 17 installed and configured.
2.  **Apache Maven:** Ensure Maven is installed for building the project.
3.  **PostgreSQL Database:**
    *   A PostgreSQL server must be running.
    *   Configure the database connection details in `src/main/resources/application.properties`:
        *   `spring.datasource.url` (e.g., `jdbc:postgresql://localhost:5432/llm_agent_db`)
        *   `spring.datasource.username` (e.g., `user`)
        *   `spring.datasource.password` (e.g., `password`)
    *   The application uses Flyway for database migrations (`spring.flyway.enabled=true`). Ensure the database user has permissions to create tables. The migrations are located in `src/main/resources/db/migration`.
4.  **OpenAI API Key:**
    *   The system uses Spring AI with OpenAI. You need a valid OpenAI API key.
    *   Set this key in `src/main/resources/application.properties` for the property `spring.ai.openai.api-key=YOUR_OPENAI_API_KEY`.
    *   Alternatively, and more securely, you can set it as an environment variable: `SPRING_AI_OPENAI_API_KEY=YOUR_OPENAI_API_KEY`. The application will pick it up.
5.  **Qdrant Vector Store (Optional but Recommended for full functionality):**
    *   The HLD and LLD mention a Qdrant vector store for memory and context.
    *   If you intend to test features relying on vector embeddings, ensure Qdrant is running and accessible.
    *   Configuration for Qdrant is in `src/main/resources/application.properties`:
        *   `spring.ai.vectorstore.qdrant.host` (e.g., `localhost`)
        *   `spring.ai.vectorstore.qdrant.port` (e.g., `6334`)
        *   `spring.ai.vectorstore.qdrant.collectionName` (e.g., `llm_agent_embeddings`)

### Building the Application:

1.  Open a terminal or command prompt.
2.  Navigate to the root directory of the `llm-agent-system` project.
3.  Run the Maven build command:
    ```bash
    mvn clean package
    ```
    This command will compile the code, run tests (if any are failing, the build may stop), and package the application into an executable JAR file in the `target/` directory (e.g., `llm-agent-system-0.0.1-SNAPSHOT.jar`).

### Running the Application:

1.  After a successful build, navigate to the directory containing the JAR file (usually `target/`).
2.  Run the application using the following command:
    ```bash
    java -jar llm-agent-system-0.0.1-SNAPSHOT.jar
    ```
    (Replace `llm-agent-system-0.0.1-SNAPSHOT.jar` with the actual JAR file name if it differs).
3.  **Important for OpenAI API Key (if not in `application.properties`):**
    If you are using an environment variable for the OpenAI API key, ensure it's set in the terminal session where you run the application:
    ```bash
    export SPRING_AI_OPENAI_API_KEY="YOUR_OPENAI_API_KEY" # For Linux/macOS
    # set SPRING_AI_OPENAI_API_KEY="YOUR_OPENAI_API_KEY" # For Windows Command Prompt
    # $env:SPRING_AI_OPENAI_API_KEY="YOUR_OPENAI_API_KEY" # For Windows PowerShell
    java -jar llm-agent-system-0.0.1-SNAPSHOT.jar
    ```

### Verifying the Application is Running:

1.  **Console Logs:** Observe the console output. Spring Boot will log its startup process. You should see messages indicating the application has started, such_as `Tomcat started on port(s): 8080 (http)`.
2.  **API Endpoint:** The `ChatController` is configured to listen at `/api/v1/chat/process`. The application typically runs on port 8080 (unless configured otherwise).
3.  **Database Connections:** Check logs for any errors related to database connectivity. Flyway migration logs should also appear, indicating successful schema setup/validation.
4.  **OpenAI API Key:** If the API key is missing or invalid, you will likely see errors related to OpenAI API calls when you try to send a message that requires LLM interaction.

---

## 2. Running the `dummy-message-sender` Application

The `dummy-message-sender` is a Java command-line application used to send messages to the main LLM Agent System's API.

### Prerequisites:

1.  **Java 17 SDK:** Ensure you have Java 17 installed and configured.
2.  **Apache Maven:** Ensure Maven is installed for building the project.
3.  **Main LLM Agent System Running:** The main application (as described in Section 1) **must** be running and accessible. By default, the dummy sender will try to connect to `http://localhost:8080/api/v1/chat/process`.

### Building the Application:

1.  Open a new terminal or command prompt (separate from the one running the main application).
2.  Navigate to the `dummy-message-sender` directory within the `llm-agent-system` project:
    ```bash
    cd dummy-message-sender
    ```
3.  Run the Maven build command:
    ```bash
    mvn clean package
    ```
    This will compile the code and package the application into an executable JAR file in its `target/` directory (e.g., `dummy-message-sender-0.0.1-SNAPSHOT.jar`).

### Running the Application:

1.  After a successful build, ensure you are still in the `dummy-message-sender` directory.
2.  Run the application using the following command:
    ```bash
    java -jar target/dummy-message-sender-0.0.1-SNAPSHOT.jar
    ```
    (Replace `dummy-message-sender-0.0.1-SNAPSHOT.jar` with the actual JAR file name if it differs).

3.  The application will start, and you will see log messages in the console, including:
    ```
    INFO com.example.dummymessagesender.DummyMessageSenderApplication - Dummy Message Sender CLI started.
    INFO com.example.dummymessagesender.DummyMessageSenderApplication - Targeting LLM Agent at: http://localhost:8080/api/v1/chat/process
    ```
4.  It will then prompt you to enter message details:
    ```
    Enter text input for the agent (or type 'exit' to quit):
    ```
    Followed by prompts for User Platform ID, Conversation Platform ID, and Source Platform.

---

## 3. Example Test Interactions and Test Cases

Once both the main LLM Agent System and the `dummy-message-sender` are running, you can start sending messages. The `dummy-message-sender` will prompt you for the following four inputs for each message:

1.  **Text input for the agent:** The actual message content you want to send.
2.  **User platform ID:** A unique identifier for the simulated user (e.g., `testuser001`, `slack-U123`).
3.  **Conversation platform ID:** A unique identifier for the simulated conversation or channel (e.g., `testconv001`, `slack-C456`).
4.  **Source platform:** The platform from which the message is supposedly originating (e.g., `dummy-java-cli`, `api-test`).

The `dummy-message-sender` will then display the agent's JSON response or any errors.

### Suggested Test Cases:

Here are some test cases you can perform. For each, note the inputs you provide and observe the `responseText` from the agent.

**A. Basic Sanity Checks:**

1.  **Test Case: Simple Greeting**
    *   **Purpose:** Verify basic connectivity and that the LLM can generate a simple response.
    *   **Inputs:**
        *   Text input: `Hello`
        *   User platform ID: `user_greeting_01`
        *   Conversation platform ID: `conv_greeting_01`
        *   Source platform: `dummy-cli`
    *   **Expected Observation:** The agent should respond with a greeting (e.g., "Hello there!", "Hi! How can I help you today?").

2.  **Test Case: Basic Question**
    *   **Purpose:** Verify the agent can process a simple question and get an answer from the LLM.
    *   **Inputs:**
        *   Text input: `What is the capital of France?`
        *   User platform ID: `user_question_01`
        *   Conversation platform ID: `conv_question_01`
        *   Source platform: `dummy-cli`
    *   **Expected Observation:** The agent should respond with "Paris" or a sentence containing this information.

**B. Testing Different Users/Conversations:**

1.  **Test Case: Follow-up in Same Conversation**
    *   First, send:
        *   Text input: `My favorite color is blue.`
        *   User platform ID: `user_context_01`
        *   Conversation platform ID: `conv_context_A`
        *   Source platform: `dummy-cli`
    *   Then, in a subsequent message (without restarting either application):
        *   Text input: `What is my favorite color?`
        *   User platform ID: `user_context_01` (same user)
        *   Conversation platform ID: `conv_context_A` (same conversation)
        *   Source platform: `dummy-cli`
    *   **Expected Observation:** If the memory/context engine is operational, the agent might recall the favorite color. *Note: This depends heavily on the main agent's memory capabilities and how context is managed.*

2.  **Test Case: Different User, Same Conversation ID (Illustrative)**
    *   Send:
        *   Text input: `I am Alice.`
        *   User platform ID: `alice_001`
        *   Conversation platform ID: `shared_conv_001`
        *   Source platform: `dummy-cli`
    *   Then send:
        *   Text input: `I am Bob.`
        *   User platform ID: `bob_001` (different user)
        *   Conversation platform ID: `shared_conv_001` (same conversation)
        *   Source platform: `dummy-cli`
    *   **Expected Observation:** This helps understand how the agent differentiates users within the same conversation context if its design supports such distinctions. Responses will vary based on agent logic.

3.  **Test Case: Same User, Different Conversation ID**
    *   Send:
        *   Text input: `Remember I like project Alpha.`
        *   User platform ID: `user_multi_conv_01`
        *   Conversation platform ID: `project_alpha_discussion`
        *   Source platform: `dummy-cli`
    *   Then send:
        *   Text input: `Do I like project Alpha?`
        *   User platform ID: `user_multi_conv_01` (same user)
        *   Conversation platform ID: `general_chat_002` (different conversation)
        *   Source platform: `dummy-cli`
    *   **Expected Observation:** If context is properly segregated by conversation ID, the agent should *not* know about project Alpha in the "general_chat_002" conversation.

**C. Testing Task-Related Functionality (If Applicable):**

*(These depend on the intents and actions configured in the main LLM Agent System, such as those described in HLD/LLD for task creation.)*

1.  **Test Case: Create a Simple Task**
    *   **Purpose:** Test if task creation intents are recognized.
    *   **Inputs:**
        *   Text input: `Remind me to buy milk tomorrow`
        *   User platform ID: `user_task_01`
        *   Conversation platform ID: `conv_task_01`
        *   Source platform: `dummy-cli`
    *   **Expected Observation:** Agent response should confirm task creation (e.g., "Okay, I've created a reminder for you to buy milk for tomorrow."). You might also want to check the database (`Tasks` table) if the task was persisted, though this is outside the scope of the dummy client's direct observation.

**D. Error Handling / Edge Cases (from the client's perspective):**

1.  **Test Case: Empty Text Input**
    *   **Purpose:** See how the agent handles blank messages. (Note: The `ProcessTextRequest` DTO in the main app has `@NotBlank` for `textInput`, so this should be caught by Spring's validation before it hits the core service if sent via API, but the dummy client itself doesn't prevent sending it if you just hit Enter). The `ConsoleInputService` in the dummy app does not explicitly prevent empty input before sending.
    *   **Inputs:**
        *   Text input: (leave blank, just press Enter)
        *   User platform ID: `user_error_01`
        *   Conversation platform ID: `conv_error_01`
        *   Source platform: `dummy-cli`
    *   **Expected Observation:** The main application should return a 400 Bad Request error. The dummy client will log this error. For example: `Error from server (400 BAD_REQUEST): {"textInput":"textInput cannot be blank", ...}` or similar validation error messages.

2.  **Test Case: Very Long Text Input**
    *   **Purpose:** Check for any length limitations or performance issues (though the dummy client itself doesn't impose limits).
    *   **Inputs:**
        *   Text input: (Paste a very long string of text, e.g., several paragraphs)
        *   User platform ID: `user_longtext_01`
        *   Conversation platform ID: `conv_longtext_01`
        *   Source platform: `dummy-cli`
    *   **Expected Observation:** The agent should process it, summarize, or truncate if designed to, or return an error if it exceeds limits. Observe response time.

### General Testing Notes:

*   **Restarting:** If you restart the main LLM Agent System, it will lose any in-memory state (like short-term conversation context not yet persisted). However, data in PostgreSQL (tasks, long-term memory if implemented) should persist.
*   **LLM Variability:** LLM responses can be non-deterministic. While the core message (e.g., "Paris") should be consistent for factual questions, the exact phrasing might vary.
*   **Check Main Application Logs:** While testing, keep an eye on the console logs of the main LLM Agent System. They will provide valuable insights into how it's processing requests and any internal errors.
*   **Check Dummy Client Logs:** The `dummy-message-sender` also logs its actions, requests sent, and responses received, which is helpful for debugging the interaction.

This list is not exhaustive but provides a good starting point for end-to-end testing. Adapt and expand these test cases based on the specific features and capabilities implemented in the LLM Agent System.

---
