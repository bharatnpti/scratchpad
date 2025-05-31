# Technical Documentation - Modular AI Agent for Mattermost

## 1. Getting Started Guide

This guide provides instructions for setting up, configuring, and running the Modular AI Agent for Mattermost.

### System Setup

*   **Prerequisites:**
    *   Mattermost Server: Version X.Y.Z or higher.
    *   Database:
        *   Relational Database (e.g., PostgreSQL 13+) for structured data (tasks, user profiles).
        *   Vector Database (e.g., Pinecone, Weaviate, Milvus) for semantic search in memory (optional, if advanced memory features are used).
    *   Message Queue: RabbitMQ 3.8+ or Kafka 2.7+ (for asynchronous task processing).
    *   Programming Language Runtime: Python 3.9+ (or other chosen backend language runtime).
    *   Git for cloning the repository.
*   **Cloning the Repository:**
    ```bash
    git clone <repository_url>
    cd modular-ai-agent-mattermost
    ```
*   **Installation of Dependencies:**
    ```bash
    # Assuming Python and pip
    pip install -r requirements.txt
    ```
    *(Provide equivalent commands if using another language, e.g., `npm install` for Node.js)*
*   **Environment Variable Configuration (`.env` file):**
    Create a `.env` file in the root directory of the project by copying `.env.example` and filling in the values:
    ```env
    # Mattermost Configuration
    MATTERMOST_URL="https://your-mattermost-instance.com"
    MATTERMOST_BOT_TOKEN="your_bot_access_token" # From Mattermost Integrations
    MATTERMOST_ADMIN_USERNAME="admin_username" # Optional, for certain admin actions
    MATTERMOST_ADMIN_PASSWORD="admin_password" # Optional

    # Database Configuration
    DATABASE_URL="postgresql://user:password@host:port/database_name" # For TaskObject, UserProfileObject, etc.
    VECTOR_DB_URL="http://vector-db-host:port" # Or API key if cloud-based
    VECTOR_DB_API_KEY="your_vector_db_api_key" # If applicable

    # Message Queue Configuration
    MESSAGE_QUEUE_URL="amqp://user:password@host:port/" # For RabbitMQ
    # MESSAGE_QUEUE_URL="kafka_host1:port1,kafka_host2:port2" # For Kafka

    # NLP Configuration
    NLP_PROVIDER="local" # or "openai", "huggingface_inference_api", etc.
    NLP_MODEL_PATH="./models/nlp_model_directory" # If local
    OPENAI_API_KEY="your_openai_api_key" # If using OpenAI
    HUGGINGFACE_API_TOKEN="your_huggingface_api_token" # If using Hugging Face API

    # Agent Operational Settings
    LOG_LEVEL="INFO" # DEBUG, INFO, WARNING, ERROR, CRITICAL
    AGENT_USER_ID="mattermost_user_id_of_the_bot" # The bot's own user ID in Mattermost

    # Plugin Specific API Keys (examples)
    JIRA_PLUGIN_API_KEY="your_jira_plugin_secret_key"
    CALENDAR_PLUGIN_API_KEY="your_calendar_plugin_secret_key"
    ```
*   **Database Schema Migration Execution:**
    ```bash
    # Assuming Alembic for Python/SQLAlchemy or similar
    alembic upgrade head
    ```
    *(Provide specific commands based on the ORM and migration tools used).*
*   **Running the Agent:**
    *   Directly:
        ```bash
        python main.py
        ```
    *   Using Docker (ensure a `Dockerfile` is present):
        ```bash
        docker build -t modular-ai-agent .
        docker run -d --env-file .env modular-ai-agent
        ```

### Configuration

*   **Plugin Registration:**
    *   Plugin configurations are typically stored in a JSON or YAML file, e.g., `config/plugins.json`. The path to this file might be configurable via an environment variable.
    *   **Format:** An array of plugin objects.
    *   **Example (`config/plugins.json`):**
        ```json
        [
          {
            "action_name": "create_jira_ticket",
            "description": "Creates a ticket in Jira.",
            "api_endpoint": "http://jira-plugin-service.internal/api/v1/create_ticket",
            "auth_type": "api_key", // "oauth2_client_credentials", "bearer_token", etc.
            "api_key_env_var": "JIRA_PLUGIN_API_KEY", // Environment variable holding the actual key
            "parameters_schema": { // Optional: JSON schema for validating parameters
              "type": "object",
              "properties": {
                "project_key": {"type": "string"},
                "summary": {"type": "string"},
                "description": {"type": "string"},
                "issue_type": {"type": "string", "default": "Task"}
              },
              "required": ["project_key", "summary"]
            },
            "timeout_seconds": 30, // Optional: specific timeout for this plugin
            "enabled": true
          },
          {
            "action_name": "create_calendar_event",
            "description": "Schedules an event in the team calendar.",
            "api_endpoint": "http://calendar-plugin.internal/api/schedule",
            "auth_type": "oauth2_client_credentials",
            "token_url_env_var": "CALENDAR_PLUGIN_TOKEN_URL",
            "client_id_env_var": "CALENDAR_PLUGIN_CLIENT_ID",
            "client_secret_env_var": "CALENDAR_PLUGIN_CLIENT_SECRET",
            "enabled": true
          }
        ]
        ```
*   **API Keys Management:**
    *   All sensitive API keys and credentials should be stored in environment variables (as shown in the `.env` example).
    *   For production deployments, use a secure secret management system like HashiCorp Vault, AWS Secrets Manager, or Google Cloud Secret Manager. The application would then fetch secrets from these services at startup.
    *   Avoid hardcoding keys in configuration files or source code.
*   **Agent Persona Configuration:**
    *   Configuration for the agent's appearance and default behaviors, typically in a YAML file, e.g., `config/persona_config.yaml`.
    *   **Example (`config/persona_config.yaml`):**
        ```yaml
        agent_display_name: "AI Project Assistant"
        # To set profile picture, do it manually in Mattermost for the bot account.
        # Or use Mattermost API if automation is required.

        default_response_style: "neutral" # formal, informal, neutral
        proactive_messaging:
          rate_limit_global: "10/minute" # Max 10 proactive messages per minute globally
          rate_limit_per_thread: "2/minute" # Max 2 proactive messages per minute in a single thread

        summarization:
          default_summary_length: "3_sentences" # short, medium, long or sentence count
          recap_message_count: 3 # Number of past messages to consider for a quick recap
          recap_threshold_duration: "P1D" # ISO 8601 duration, e.g., 1 day. After this period of inactivity, agent provides a recap.


        task_management:
          default_check_in_frequency: "P1D" # ISO 8601 duration for 1 day
          max_check_in_attempts_before_escalation: 3
          blocker_duration_before_escalation: "P3D" # 3 days

        greeting_message: "Hello! I am the AI Project Assistant. Mention me with @{agent_display_name} for help."
        ```

## 2. Component API Docs

This section details the APIs for the core components as defined in the Low-Level Design (LLD).

### `ThreadMemoryManager` API

*   **`get_thread_memory(thread_id: str) -> ThreadMemoryObject | None`**
    *   Description: Retrieves the complete memory object for a given Mattermost thread.
    *   Params: `thread_id` (str) - The unique identifier of the Mattermost thread.
    *   Returns: `ThreadMemoryObject` (as defined in LLD Data Models) if found, otherwise `None`.
    *   Example: `memory = memory_manager.get_thread_memory("thd_abc123xyz")`
*   **`add_message_to_thread(thread_id: str, message: MessageObject) -> ThreadMemoryObject | None`**
    *   Description: Adds a new `MessageObject` to the specified thread's memory, triggers NLP, and updates summaries/snapshots.
    *   Params:
        *   `thread_id` (str) - The ID of the Mattermost thread.
        *   `message` (MessageObject) - The message object to add.
    *   Returns: The updated `ThreadMemoryObject` on success, `None` on failure.
*   **`create_empty_thread_memory(thread_id: str, channel_id: str, initial_message: MessageObject) -> ThreadMemoryObject`**
    *   Description: Initializes a new memory record for a thread.
    *   Params:
        *   `thread_id` (str) - ID of the new Mattermost thread.
        *   `channel_id` (str) - ID of the channel.
        *   `initial_message` (MessageObject) - The first message object.
    *   Returns: The newly created `ThreadMemoryObject`.
*   **`update_thread_summary(thread_id: str) -> None`**
    *   Description: Explicitly triggers a regeneration or update of the `thread_summary` for the given thread.
    *   Params: `thread_id` (str) - The ID of the Mattermost thread.
    *   Returns: None.
*   **`find_related_threads(query_embedding: VECTOR, threshold: float, top_n: int = 5) -> list[ThreadMemoryObject]`**
    *   Description: Searches for threads semantically similar to a query embedding.
    *   Params:
        *   `query_embedding` (VECTOR) - Vector embedding of the query.
        *   `threshold` (float) - Minimum similarity score.
        *   `top_n` (int) - Max number of results.
    *   Returns: A list of `ThreadMemoryObject` instances.
*   **`get_conversation_snapshot(thread_id: str, snapshot_id: str | None = None) -> ConversationContextSnapshotObject | None`**
    *   Description: Retrieves a `ConversationContextSnapshotObject`. If `snapshot_id` is None, retrieves the latest for the `thread_id` (based on `ThreadMemoryObject.current_context_snapshot_id`).
    *   Params:
        *   `thread_id` (str) - ID of the thread.
        *   `snapshot_id` (str | None, optional) - Specific snapshot ID to retrieve.
    *   Returns: `ConversationContextSnapshotObject` or `None`.
*   **`create_conversation_snapshot(thread_id: str) -> ConversationContextSnapshotObject | None`**
    *   Description: Creates a new snapshot of the current thread context (active users, topics, recent tasks, sentiment) and updates `ThreadMemoryObject.current_context_snapshot_id`.
    *   Params: `thread_id` (str) - ID of the thread for which to create a snapshot.
    *   Returns: The created `ConversationContextSnapshotObject` or `None` if thread not found.

### `TaskStateTracker` API

*   **`create_task(task_details: dict) -> TaskObject | None`**
    *   Description: Creates a new task.
    *   Params: `task_details` (dict) - Attributes for the new task (Refer to `TaskObject` in LLD).
    *   Returns: The created `TaskObject` or `None`.
    *   Example: `new_task = tracker.create_task({"title": "Review PR", "assigned_to_user_id": "usr_carol"})`
*   **`get_task(task_id: str) -> TaskObject | None`**
    *   Description: Retrieves a specific task by ID.
    *   Params: `task_id` (str) - ID of the task.
    *   Returns: `TaskObject` or `None`.
*   **`update_task(task_id: str, updates: dict) -> TaskObject | None`**
    *   Description: Updates attributes of an existing task.
    *   Params:
        *   `task_id` (str) - ID of the task.
        *   `updates` (dict) - Key-value pairs of attributes to update.
    *   Returns: Updated `TaskObject` or `None`.
*   **`assign_task(task_id: str, assignee_user_id: str, assigning_user_id: str) -> TaskObject | None`**
    *   Description: Assigns or reassigns a task.
    *   Params:
        *   `task_id` (str) - Task ID.
        *   `assignee_user_id` (str) - User ID of the assignee.
        *   `assigning_user_id` (str) - User ID of the assigner.
    *   Returns: Updated `TaskObject`.
*   **`add_task_dependency(task_id: str, dependent_task_id: str) -> TaskObject | None`**
    *   Description: Adds a dependency to a task. `dependent_task_id` must be completed before `task_id`.
    *   Params:
        *   `task_id` (str) - The task that will depend on another.
        *   `dependent_task_id` (str) - The task that must be completed first.
    *   Returns: Updated `TaskObject` for `task_id`, or `None` on failure.
*   **`get_tasks_for_user(user_id: str, status_filter: list[str] = None) -> list[TaskObject]`**
    *   Description: Retrieves tasks assigned to a user, optionally filtered by status.
    *   Params:
        *   `user_id` (str) - User ID.
        *   `status_filter` (list[str], optional) - List of statuses to filter by.
    *   Returns: A list of `TaskObject` instances.
*   **`get_tasks_by_criteria(criteria: dict) -> list[TaskObject]`**
    *   Description: Retrieves tasks based on flexible criteria.
    *   Params: `criteria` (dict) - Key-value pairs for filtering.
    *   Returns: A list of `TaskObject` instances.

### `ActionExecutor` API

*   **`execute_action(action_name: str, parameters: dict, task_id: str | None = None) -> dict`**
    *   Description: Executes a registered internal or plugin action.
    *   Params:
        *   `action_name` (str) - Name of the action.
        *   `parameters` (dict) - Parameters required by the action.
        *   `task_id` (str | None, optional) - ID of the associated task, if any.
    *   Returns: Dictionary with execution status:
        *   Success (immediate): `{"status": "success", "result": <data>, "message": "..."}`
        *   Pending (queued): `{"status": "pending", "job_id": <id>, "message": "..."}`
        *   Error: `{"status": "error", "message": "..."}`
    *   Example: `status = executor.execute_action("create_calendar_event", params, task_id="tsk_123")`
*   **`get_action_status(job_id: str) -> dict`** (For asynchronous actions)
    *   Description: Retrieves status of a queued action.
    *   Params: `job_id` (str) - Job identifier.
    *   Returns: Dictionary with current status (e.g., `{"status": "in_progress"}`).
*   **`register_plugin(plugin_details: dict, overwrite: bool = False) -> bool`**
    *   Description: Dynamically registers/updates a plugin's configuration (typically used at startup from config files).
    *   Params:
        *   `plugin_details` (dict) - Plugin metadata.
        *   `overwrite` (bool) - If `True`, overwrite existing plugin with same `action_name`.
    *   Returns: `True` on success, `False` otherwise.

### `AgentPersona` API (Key Functions)

*   **`generate_response(context_snapshot: ConversationContextSnapshotObject, intent: str, data: dict) -> str`**
    *   Description: Generates a human-like text response for Mattermost.
    *   Params:
        *   `context_snapshot` (ConversationContextSnapshotObject) - Current conversation state.
        *   `intent` (str) - Agent's communicative goal (e.g., `confirm_task_creation`).
        *   `data` (dict) - Relevant information (e.g., `TaskObject`).
    *   Returns: Formatted string for Mattermost.
*   **`summarize_thread_for_display(thread_memory: ThreadMemoryObject, style: str = "default") -> str`**
    *   Description: Creates a concise thread summary for display.
    *   Params:
        *   `thread_memory` (ThreadMemoryObject) - Memory object of the thread.
        *   `style` (str, optional) - "default", "brief", "detailed".
    *   Returns: Summary string.
*   **`format_task_update_message(task_object: TaskObject) -> str`**
    *   Description: Generates a user-friendly message for a task update.
    *   Params: `task_object` (TaskObject) - The updated task.
    *   Returns: Formatted string.
*   **`ask_clarification_message(context_snapshot: ConversationContextSnapshotObject, ambiguous_query: str, required_info_list: list[str]) -> str`**
    *   Description: Constructs a message asking for more information.
    *   Params:
        *   `context_snapshot` (ConversationContextSnapshotObject) - Current conversation state.
        *   `ambiguous_query` (str) - The user's unclear query.
        *   `required_info_list` (list[str]) - Specific information needed.
    *   Returns: Formatted question string.
*   **`generate_recap_message(thread_memory: ThreadMemoryObject, agent_last_active_timestamp: DATETIME) -> str`**
    *   Description: Creates a recap message for re-entering threads.
    *   Params:
        *   `thread_memory` (ThreadMemoryObject) - Memory of the re-entered thread.
        *   `agent_last_active_timestamp` (DATETIME) - When agent was last active here.
    *   Returns: Formatted recap string.

## 3. Customization Guide

### Adding New Types of Tasks

1.  **Modify Data Model:**
    *   Open `LLD.md`, update `TaskObject` definition. Update database schema (e.g., Alembic migration).
        ```bash
        # alembic revision -m "add_custom_fields_to_tasks"
        # Edit script, then: alembic upgrade head
        ```
2.  **Update `TaskStateTracker`:** Modify methods to handle new fields.
3.  **Adjust NLP Intent Recognition:** Update NLP model training data if new keywords identify these tasks.

### Adding New Memory Schemas

1.  **Update Data Models (`LLD.md`):** Extend `ThreadMemoryObject` or `ConversationContextSnapshot`, or link new objects. Update database schema.
2.  **Modify `ThreadMemoryManager`:** Adjust methods to populate/process new schema elements.
3.  **Impact on NLP and Logic:** Update NLP components and agent logic to use new memory information.

### Adding New Integrations (Plugins)

1.  **Develop Plugin Service:** Create the external service with an API (REST, gRPC).
2.  **Register Plugin:** Add entry to `config/plugins.json` (action_name, endpoint, auth details, parameter schema). Add API keys to `.env`/secrets manager.
3.  **Agent Logic (Optional):**
    *   Define new NLP intent if users should request action via natural language.
    *   Link to tasks if applicable.
    *   Implement proactive use logic if agent should autonomously use the plugin.
    *   Update `AgentPersona` for specific response formatting if needed.

## 4. Operational Guide

### Monitoring Agent Behavior

*   **Key Metrics:** Messages processed, tasks created/completed, action success/failure rates, API latencies, error rates.
*   **Tools:** Prometheus, Grafana for metrics; ELK stack or similar for logs.
*   **Dashboards:** Overview, Performance, Task, Error dashboards.

### Logs

*   **Log Levels:** `DEBUG`, `INFO`, `WARNING`, `ERROR`, `CRITICAL` (via `LOG_LEVEL` env var).
*   **Log Format:** Structured JSON.
    ```json
    {
      "timestamp": "2023-12-01T10:05:30.123Z",
      "level": "INFO",
      "module": "ThreadMemoryManager",
      "thread_id": "thd_abc123xyz", // Optional
      "task_id": "tsk_456", // Optional
      "message_id": "msg_789", // Optional
      "correlation_id": "corr_id_001",
      "message": "Successfully added message to thread memory."
      // "exception_type": "...", "stack_trace": "..." // For errors
    }
    ```
*   **Key Information:** Incoming messages, NLP results, task changes, action executions, external API calls, errors.

### Error Handling

*   **Retry Mechanisms:** Exponential backoff for transient errors in external API calls.
*   **Dead-Letter Queues (DLQs):** For critical asynchronous task failures.
*   **Alerting:** For critical errors, high API error rates, queue depth issues (PagerDuty, email, Mattermost admin channel).

### Rollback Scenarios

*   **Code Deployment:** Blue/green or canary releases. Automated tests. Clear rollback plan.
*   **Data Migration:** Back up data. Write reversible migration scripts. Test in staging.
*   **Plugin Failure:** `ActionExecutor` isolates plugin failures. Disable faulty plugins via config. Agent notifies admins.

## 5. UX/Interaction Docs

This section describes how users will interact with the AI agent in Mattermost.

### Expected Thread Behavior

*   **Primary Interaction Locus:** Agent interacts within existing Mattermost threads.
*   **Thread Initiation:** Generally does not initiate new channels. May start new *threads* for complex goals if designed.
*   **Posting Cadence:** Posts when mentioned, has relevant task updates, needs clarification, performs scheduled check-ins, or provides recaps.

### Examples of Agent Responses

*   **Task Creation (User invokes):**
    *   User: "@agent Can you remind @david to submit his expenses by Friday?"
    *   Agent: "Okay @user, I've created a task for @david: 'Submit expenses' due this Friday. I'll send him a reminder."
*   **Status Update (Proactive):**
    *   Agent: "(In 'Project Alpha Launch' thread) Quick update on 'Finalize press release': @emily completed the draft, now pending review by @frank. Expected completion: Tomorrow EOD."
*   **Status Update (On-demand):**
    *   User: "@agent status on 'Setup new CI server'?"
    *   Agent: "The task 'Setup new CI server' (assigned to @bob) is 'in_progress'. Last update: 'Base OS installation complete'. @bob, any news?"
*   **Asking for Clarification:**
    *   User: "@agent help with marketing campaign."
    *   Agent: "I can help with that! To get started, could you tell me a bit more? E.g., what specific part of the marketing campaign (brainstorming, content drafting, scheduling)?"
*   **Summarization (Re-entry):**
    *   User: (In inactive thread) "@agent can we get an update on this?"
    *   Agent: "Hi @user, rejoining this thread on 'Q3 Budget Planning'. Last I recall, we discussed software license allocation (assignee: @sara). Goal: finalize budget by last week. What specific update are you looking for?"
*   **Error Notification:**
    *   Agent: "I tried to create the 'Team Meeting' event in Google Calendar but encountered an issue: 'Could not authenticate'. I'll try again in 15 minutes. If it persists, manual creation might be needed."
*   **Implicit Task Suggestion (Advanced):**
    *   User A: "We should probably get the new designs reviewed by legal."
    *   Agent (if configured for high proactivity): "I noticed a potential task: 'Get new designs reviewed by legal'. Would you like me to create this task?"

### Principles for Clarity, Summarization, and Non-Intrusiveness

*   **Clarity:** Simple, direct language. Clear attribution. Use Mattermost formatting. Use @mentions.
*   **Summarization:** Contextual recaps (1-2 sentences). Digest updates for multiple small changes. Avoid redundancy.
*   **Non-Intrusiveness:** Primarily mention-driven. Consolidate information. Respectful timing. Configurable verbosity (future). Smart check-ins (reasonable intervals). Clear identification for muting. Graceful error handling (concise messages in thread, details in logs).
```
