# LLM Agent System

## Dummy Message Sender (Java Test Client)

The `dummy-message-sender` is a command-line Java application designed to test the main LLM Agent System. It allows you to manually send messages to the main application's API endpoint (`/api/v1/chat/process`) and view the agent's responses.

### Prerequisites
- Java 17 or higher installed.
- Apache Maven installed.

### Setup and Running
1.  **Navigate to the directory:**
    ```bash
    cd dummy-message-sender
    ```

2.  **Build the application:**
    Use Maven to compile the application and package it into a JAR file.
    ```bash
    mvn clean package
    ```

3.  **Run the application:**
    Execute the JAR file using Java.
    ```bash
    java -jar target/dummy-message-sender-0.0.1-SNAPSHOT.jar
    ```

### Interacting with the Dummy Sender
Once running, the application will prompt you for the following information for each message:
-   **Text input for the agent:** The message you want to send.
-   **User platform ID:** A unique identifier for the user (e.g., `user123`).
-   **Conversation platform ID:** A unique identifier for the conversation (e.g., `conv789`).
-   **Source platform:** The platform from which the message originates (e.g., `dummy-java-app`).

Type `exit` as the text input to quit the application.

### Important Note
The main LLM Agent System application **must be running and accessible** at `http://localhost:8080/api/v1/chat/process` for the `dummy-message-sender` to successfully send messages and receive responses. If the main application is not running or the endpoint is incorrect, the dummy sender will show an error.
