# Low-Level Design (LLD) - Modular AI Agent for Mattermost

## 1. Data Models

This section defines the core data structures used by the AI agent system.

### Thread Memory Object

Represents the agent's understanding and memory of a specific Mattermost thread.

*   `thread_id`: STRING (Primary Key) - Unique identifier for the Mattermost thread.
*   `channel_id`: STRING - Identifier for the channel containing the thread.
*   `messages`: LIST of Message Objects - Chronological list of messages within the thread.
    *   `message_id`: STRING - Unique identifier for the message.
    *   `user_id`: STRING - Identifier of the user who sent the message.
    *   `timestamp`: DATETIME - Time the message was posted.
    *   `text_content`: STRING - Raw text of the message.
    *   `entities`: LIST of Entity Objects - Named entities extracted from the message.
        *   `entity_value`: STRING (e.g., "Bob", "report", "Friday")
        *   `entity_type`: STRING (e.g., "person", "task_keyword", "date")
        *   `start_char_offset`: INTEGER
        *   `end_char_offset`: INTEGER
    *   `intent`: STRING - Inferred intent of the message (e.g., `create_task`, `ask_clarification`, `provide_update`, `informational`).
    *   `summary_embedding`: VECTOR (Optional) - Embedding of the message content for semantic search or similarity comparison.
*   `participants`: LIST of User IDs - Users who have participated in the thread.
*   `thread_summary`: TEXT - A concise summary of the thread's main topics and objectives, updated periodically.
*   `last_activity_timestamp`: DATETIME - Timestamp of the most recent message or agent activity in the thread.
*   `related_thread_ids`: LIST of STRING - IDs of other threads that are contextually related.
*   `current_context_snapshot_id`: STRING (Optional) - Foreign key to the latest ConversationContextSnapshot for this thread.

### Task Object

Represents a task identified and managed by the AI agent.

*   `task_id`: STRING (Primary Key) - Unique identifier for the task.
*   `title`: STRING - A brief, descriptive title for the task.
*   `description`: TEXT - Detailed description of the task.
*   `created_by_user_id`: STRING - User ID of the person who initiated or requested the task.
*   `assigned_to_user_id`: STRING (Optional) - User ID of the person responsible for the task. Can be a specific user or a generic 'agent' identifier if the agent is executing it.
*   `status`: ENUM ('open', 'in_progress', 'blocked', 'completed', 'cancelled', 'pending_clarification', 'error') - Current state of the task.
*   `priority`: ENUM ('high', 'medium', 'low') - Priority level of the task.
*   `due_date`: DATETIME (Optional) - Deadline for task completion.
*   `dependencies`: LIST of Task IDs - Other tasks that must be completed before this task can start/finish.
*   `blockers`: LIST of STRING - Text descriptions of issues preventing task progress.
*   `associated_thread_id`: STRING - The Mattermost thread ID where this task originated or is primarily discussed.
*   `creation_timestamp`: DATETIME - When the task was created.
*   `last_updated_timestamp`: DATETIME - When the task was last modified.
*   `check_in_frequency`: DURATION (Optional) - How often the agent should request an update (e.g., 'P1D' for daily, 'P3D' for every 3 days, using ISO 8601 duration format).
*   `last_check_in_timestamp`: DATETIME (Optional) - Timestamp of the last agent-initiated check-in.
*   `sub_tasks`: LIST of Task IDs - For breaking down complex tasks into smaller, manageable parts.
*   `action_name`: STRING (Optional) - If the task is directly executable by the agent, this specifies the action (e.g., "create_jira_ticket", "send_email").
*   `action_parameters`: JSON (Optional) - Parameters for the `action_name`.
*   `execution_logs`: LIST of LogEntry Objects (Optional)
    *   `timestamp`: DATETIME
    *   `log_message`: STRING
    *   `status`: STRING (e.g., 'info', 'error', 'warning', 'success_plugin', 'error_plugin')

### Conversation Context Snapshot Object

Captures the state of a conversation at a specific point in time, useful for re-entry and dynamic responses.

*   `snapshot_id`: STRING (Primary Key) - Unique identifier for the snapshot.
*   `thread_id`: STRING (Foreign Key to ThreadMemoryObject) - The thread this snapshot belongs to.
*   `timestamp`: DATETIME - Time the snapshot was taken.
*   `active_users_in_thread_at_snapshot`: LIST of User IDs - Users who were recently active or mentioned around the time of the snapshot.
*   `current_topic_keywords_at_snapshot`: LIST of STRING - Key terms or topics dominating the conversation at this point.
*   `recent_tasks_mentioned_ids`: LIST of Task IDs - Tasks discussed or updated recently.
*   `sentiment_score_at_snapshot`: FLOAT (Optional) - Overall sentiment of the recent messages (e.g., -1.0 for negative, 1.0 for positive).
*   `pending_questions_for_user`: MAP (User ID -> List of Question Objects) - Questions the agent has asked specific users in the thread that are awaiting answers.
    *   `question_id`: STRING
    *   `question_text`: STRING
    *   `asked_by_agent_message_id`: STRING - Message ID where the agent asked this question.
    *   `timestamp_asked`: DATETIME

### User Profile Object

Stores information about Mattermost users, primarily for identity resolution and personalization.

*   `user_id`: STRING (Primary Key) - Mattermost User ID.
*   `canonical_user_id`: STRING (Optional) - A system-wide unique ID if merging multiple platform identities. Defaults to `user_id`.
*   `full_name`: STRING (Optional) - User's full name, if available.
*   `email`: STRING (Optional) - User's email, if available and permitted.
*   `aliases`: LIST of STRING (e.g., nicknames, common @mentions used by others for this user).
*   `preferences`: JSON (Optional) - User-specific preferences for agent interaction (e.g., notification frequency, preferred summary length).
*   `last_seen_timestamp`: DATETIME (Optional) - Last time the user interacted with the agent or was seen in a monitored thread.

## 2. Class/Module Breakdown

This section outlines the main classes and modules with their responsibilities.

### `ThreadMemoryManager` Class

Manages the lifecycle and retrieval of `ThreadMemoryObject` instances and associated `ConversationContextSnapshotObject`s.

*   **Properties:**
    *   `db_connection`: Connection object for the database storing thread memories (e.g., PostgreSQL, MongoDB, Vector DB interface).
    *   `nlp_service`: Client for NLP processing (entity extraction, intent recognition, summarization, embedding generation).
*   **Methods:**
    *   `get_thread_memory(thread_id: str) -> ThreadMemoryObject | None`:
        *   Retrieves a `ThreadMemoryObject` from the database.
    *   `add_message_to_thread(thread_id: str, message: MessageObject) -> ThreadMemoryObject`:
        *   Retrieves the `ThreadMemoryObject`. Appends `message`. Updates `participants`, `last_activity_timestamp`.
        *   Triggers NLP processing for the new message (entities, intent) and updates the `message` object in memory.
        *   Triggers an update to `thread_summary` and potentially a new `ConversationContextSnapshot` if significant new information is added or after a certain number of new messages.
        *   Saves the updated `ThreadMemoryObject`.
    *   `create_empty_thread_memory(thread_id: str, channel_id: str, initial_message: MessageObject) -> ThreadMemoryObject`:
        *   Creates a new `ThreadMemoryObject` with the `initial_message`.
    *   `update_thread_summary(thread_id: str) -> None`:
        *   Uses `nlp_service` to generate/update the `thread_summary` in the `ThreadMemoryObject`.
    *   `find_related_threads(query_embedding: VECTOR, threshold: float, max_results: int = 5) -> list[ThreadMemoryObject]`:
        *   Queries a vector database for threads with similar summary embeddings.
    *   `get_conversation_snapshot(thread_id: str, snapshot_id: str = None) -> ConversationContextSnapshotObject | None`:
        *   Retrieves a specific `ConversationContextSnapshotObject`. If `snapshot_id` is None, retrieves the latest for the `thread_id`.
    *   `create_conversation_snapshot(thread_id: str) -> ConversationContextSnapshotObject`:
        *   Creates a new snapshot of the current thread context (active users, topics, recent tasks, sentiment).

### `TaskStateTracker` Class

Manages the lifecycle and state of `TaskObject` instances.

*   **Properties:**
    *   `db_connection`: Connection object for the database storing task objects.
*   **Methods:**
    *   `create_task(task_details: dict) -> TaskObject`:
        *   Validates `task_details`. Creates and saves a new `TaskObject`.
    *   `get_task(task_id: str) -> TaskObject | None`:
        *   Retrieves a `TaskObject`.
    *   `update_task(task_id: str, updates: dict) -> TaskObject | None`:
        *   Fetches and updates the task with validated changes.
    *   `assign_task(task_id: str, assignee_user_id: str, assigning_user_id: str) -> TaskObject | None`:
        *   Updates `assigned_to_user_id` and logs the change.
    *   `add_task_dependency(task_id: str, dependent_task_id: str) -> TaskObject | None`:
        *   Adds `dependent_task_id` to the `dependencies` list.
    *   `get_tasks_for_user(user_id: str, status_filter: list[str] = None) -> list[TaskObject]`:
        *   Queries tasks assigned to `user_id`, optionally filtered by `status`.
    *   `get_tasks_by_criteria(criteria: dict) -> list[TaskObject]`:
        *   Generic method to fetch tasks based on various criteria (e.g., due date, status, priority).

### `ActionExecutor` Class

Responsible for executing actions, either internally or by delegating to plugins.

*   **Properties:**
    *   `plugin_registry`: Stores metadata about registered plugins.
    *   `message_queue_client`: Client for asynchronous task processing via a message queue.
    *   `task_tracker`: Instance of `TaskStateTracker` for updating task status.
*   **Methods:**
    *   `execute_action(task_id: str, action_name: str, parameters: dict) -> dict`:
        *   Logs attempt to execute. Looks up `action_name` in `plugin_registry`.
        *   **Internal Action:** Executes directly. Updates task status via `task_tracker`. Returns `{"status": "success/error", "result": ..., "message": ...}`.
        *   **Plugin Action (HTTP Call):** Constructs request, makes API call (with retries). Updates task. Returns `{"status": "success/error", "result": ..., "message": ...}`.
        *   **Long-Running/Queued Plugin Action:** Pushes to `message_queue`. Updates task status to 'in_progress'. Returns `{"status": "pending", "job_id": ..., "message": "Action queued."}`.
    *   `_handle_plugin_response(task_id: str, response: HttpResponse | PluginCallbackPayload) -> None`:
        *   Processes responses/callbacks from plugins. Updates task status via `task_tracker`.
    *   `register_plugin(plugin_details: dict) -> bool`:
        *   Adds/updates a plugin's information in `plugin_registry`.
    *   `get_action_status(job_id: str) -> dict`: (For queued actions)
        *   Queries the status of a job from the message queue or a temporary status store.

### `AgentPersona` Module (Handles UX Layer Logic)

Contains functions for generating natural language responses and managing user-facing interactions.

*   **Properties (Conceptual - passed as arguments or accessed via service locators):**
    *   `nlp_service`: For language understanding and generation.
    *   `user_profile_resolver`: Utility to get user details (e.g., mentions) from `UserProfileObject`.
    *   `config`: Access to `persona_config.yaml` values.
*   **Functions:**
    *   `generate_response(context_snapshot: ConversationContextSnapshotObject, intent: str, data: dict) -> str`:
        *   Constructs a human-readable Mattermost message based on intent, data, and persona config.
        *   Example: Uses `user_profile_resolver` to get assignee's @mention for task confirmations.
    *   `summarize_thread_for_display(thread_memory: ThreadMemoryObject, style: str = "default") -> str`:
        *   Returns a concise summary from `thread_memory.thread_summary` or generates one, formatted for display according to `style` and persona config.
    *   `format_task_update_message(task_object: TaskObject) -> str`:
        *   Generates a user-friendly message for task updates.
    *   `ask_clarification_message(context_snapshot: ConversationContextSnapshotObject, ambiguous_query: str, required_info: list[str]) -> str`:
        *   Generates a message asking for more information.
    *   `generate_recap_message(thread_memory: ThreadMemoryObject, agent_last_active_timestamp_in_thread: DATETIME) -> str`:
        *   Generates a context-setting recap message for re-entering threads.

### `UserProfileManager` (Utility Class/Module)

Provides methods for managing and resolving user profiles. Could be a standalone class or integrated within `ThreadMemoryManager`.

*   **Properties:**
    *   `db_connection`: Connection to the database storing `UserProfileObject`s.
*   **Methods:**
    *   `get_user_profile(user_id: str) -> UserProfileObject | None`
    *   `get_user_profile_by_alias(alias: str) -> UserProfileObject | None`
    *   `resolve_user_mention(mention_string: str) -> UserProfileObject | None`: (Uses NLP and DB lookup)
    *   `create_user_profile(user_details: dict) -> UserProfileObject`
    *   `update_user_profile(user_id: str, updates: dict) -> UserProfileObject | None`
    *   `add_alias_to_profile(user_id: str, alias: str) -> None`


## 3. Algorithms

### Identity Resolution across Threads/Nicknames

1.  **Data Store:** `UserProfileObject` table/collection, managed by `UserProfileManager`.
2.  **Message Processing:**
    a.  NLP service extracts potential user mentions (e.g., "@username", "John Doe") from `MessageObject.text_content`.
    b.  For each mention, call `UserProfileManager.resolve_user_mention(mention)`.
    c.  **Resolution Logic within `UserProfileManager.resolve_user_mention`:**
        i.  Direct match on `UserProfileObject.user_id` (if mention is like "@user_id_actual").
        ii. Match against `UserProfileObject.aliases`.
        iii. NLP-based name matching against `UserProfileObject.full_name`.
        iv. If ambiguous, flag for agent to ask for clarification.
        v.  If no match but clearly a Mattermost @mention, query Mattermost API for user details and create a basic profile using `UserProfileManager.create_user_profile()`.
    d.  **Learning Aliases:** If context suggests a new alias for a known user, `UserProfileManager.add_alias_to_profile()` can be called (possibly after agent confirmation).

### Smart Task Check-ins + Escalation Logic

1.  **Identify Tasks for Check-in:**
    *   Scheduled job calls `TaskStateTracker.get_tasks_by_criteria()` with logic: `status` in ('open', 'in_progress'), `check_in_frequency` set, `last_check_in_timestamp` is older than `now() - check_in_frequency`, etc.
2.  **Perform Check-in:**
    *   For each task: `task = TaskStateTracker.get_task(task_id)`.
    *   `snapshot = ThreadMemoryManager.get_conversation_snapshot(thread_id=task.associated_thread_id)` (or create one).
    *   `message_text = AgentPersona.generate_task_check_in_message(task, snapshot)` (e.g., "Hi @{assignee_mention}, any updates on '{task_title}'?").
    *   Agent posts message to `task.associated_thread_id`.
    *   `TaskStateTracker.update_task(task_id, {"last_check_in_timestamp": now()})`.
3.  **Process Responses:**
    *   `ThreadMemoryManager` processes replies. NLP service analyzes for status cues.
    *   If status update detected, `TaskStateTracker.update_task()` with new status, `blockers`, etc.
4.  **Escalation Logic:**
    *   Scheduled job identifies tasks for escalation (blocked too long, overdue, no response to check-ins).
    *   For each: `task = TaskStateTracker.get_task(task_id)`.
    *   `snapshot = ThreadMemoryManager.get_conversation_snapshot(thread_id=task.associated_thread_id)`.
    *   `escalation_text = AgentPersona.generate_task_escalation_message(task, snapshot)` (e.g., "Task '{task_title}' for @{assignee_mention} is overdue. CC @{creator_mention}").
    *   Agent posts message. Log escalation in `task.execution_logs`.

### Context-Aware Re-entry Mechanism

1.  **Trigger:** Agent mentioned or needs to post proactively in an old/inactive thread.
2.  **Gather Context:**
    *   `thread_memory = ThreadMemoryManager.get_thread_memory(thread_id)`.
    *   Determine `agent_last_active_timestamp_in_thread`.
3.  **Generate Recap (if needed):**
    *   Condition: `now() - agent_last_active_timestamp_in_thread > persona_config.recap_threshold_duration`.
    *   `recap_message = AgentPersona.generate_recap_message(thread_memory, agent_last_active_timestamp_in_thread)`.
4.  **Post and Proceed:** Agent posts `recap_message`, then its primary message.

## 4. Interaction Workflows

### New Task Initiation in Thread

1.  **User Message:** "@agent Please ask @Bob to send the report by Friday. It's for Project Phoenix."
2.  **Message Processing (`ThreadMemoryManager` + NLP Service):** Extracts intent (`create_task`), entities (`@Bob` as assignee_mention, "send the report" as core_description, "Friday" as due_date_phrase, "Project Phoenix" as context).
3.  **Identity Resolution (`UserProfileManager`):** Resolves "@Bob" to Bob's `user_id`.
4.  **Parameter Assembly:** `title`: "Send report (Project Phoenix)", `description`: "User @Alice asked @Bob to send the report...", `assigned_to_user_id`: Bob's ID, `due_date`: resolved Friday, `created_by_user_id`: Alice's ID, `associated_thread_id`: current thread.
5.  **Task Creation (`TaskStateTracker.create_task`):** Creates `TaskObject`.
6.  **Confirmation (`AgentPersona`):**
    *   `snapshot = ThreadMemoryManager.get_conversation_snapshot(thread_id=current_thread_id)`.
    *   `confirmation_message = AgentPersona.generate_response(snapshot, "confirm_task_creation", {"task": created_task_object})`.
    *   Agent posts: "Okay @Alice, I've created a task for @Bob: 'Send report (Project Phoenix)' due {Friday_date}."

### Rejoining Old Threads with Recaps

1.  **User Message:** (In a month-old thread) "@agent what was the outcome of the design discussion here?"
2.  **Context Retrieval & Recap Generation (as per Context-Aware Re-entry Mechanism):** Agent posts recap: "Hi @Alice, rejoining this thread about V1 design. Last time we discussed options A & B... The thread summary is: '{summary}'."
3.  **Address Question:** Agent then processes the question, searching `thread_memory.messages` or related tasks. Posts answer: "The final decision recorded was {decision}." or "I couldn't find an explicit final decision..."

### Handling Failed API Calls + Retries (within `ActionExecutor`)

1.  **Trigger:** `ActionExecutor.execute_action(task_id="T123", action_name="create_jira_ticket", params={...})`.
2.  **Plugin Call Fails** (e.g., timeout).
3.  **Retry Logic:** `ActionExecutor` retries N times with exponential backoff. Logs attempts in `TaskObject.execution_logs` via `TaskStateTracker.update_task()`.
4.  **Retries Exhausted:** `TaskStateTracker.update_task(task_id, {"status": "error", "blockers": ["Failed to create Jira ticket: {final_error}"], ...})`.
5.  **Notification (`AgentPersona`):**
    *   `snapshot = ThreadMemoryManager.get_conversation_snapshot(thread_id=task.associated_thread_id)`.
    *   `error_message = AgentPersona.generate_response(snapshot, "action_execution_failed", {"task_id": "T123", "action_name": "create_jira_ticket", "error": "{final_error}"})`.
    *   Agent posts `error_message`.

## 5. Plugin Architecture

### How New Actions Can Be Added

1.  **Develop Plugin Service:** Expose a well-defined API (e.g., REST endpoint).
2.  **Define Interface Contract:** Specify `action_name`, `parameters` (JSON schema recommended), success/error response formats.
3.  **Register Plugin (`ActionExecutor.register_plugin` or via config file):**
    Provide `action_name`, `api_endpoint_url`, `authentication_details` (e.g., API key name in env), `request_schema`.
    ```json
    // Example in plugins.json
    {
      "action_name": "create_jira_ticket",
      "api_endpoint": "http://jira-plugin/api/create",
      "auth": {"type": "api_key", "header": "X-Api-Key", "key_env_var": "JIRA_API_KEY"}
    }
    ```
4.  **Usage:** Agent's `ActionExecutor` uses this registry to make calls.

### Interface Contracts (Example for a "Calendar Event Creation" plugin)

*   **Request (from `ActionExecutor` to Plugin):**
    ```json
    {
      "action_name": "create_calendar_event", // For plugin's internal routing if it handles multiple
      "parameters": {
        "title": "Team Meeting",
        "attendees_user_ids": ["mattermost_user_id1", "mattermost_user_id2"], // Plugin may need to resolve these to emails
        "start_time_utc": "2023-12-01T10:00:00Z",
        "end_time_utc": "2023-12-01T11:00:00Z",
        "description": "Discuss Q4 roadmap"
      },
      "context": {
        "requesting_mattermost_user_id": "requesting_user_id",
        "mattermost_thread_id": "thread_abc123",
        "task_id": "task_def456" // For callback correlation
      }
      // Authentication is usually handled via HTTP headers (e.g., API Key, Bearer Token)
      // by the ActionExecutor based on plugin registration details.
    }
    ```
*   **Response (from Plugin to `ActionExecutor`):**
    *   Success:
        ```json
        {
          "status": "success",
          "data": {
            "event_id": "cal_event_xyz789",
            "confirmation_message": "Calendar event 'Team Meeting' created."
          }
        }
        ```
    *   Failure:
        ```json
        {
          "status": "error",
          "error_message": "Failed to authenticate with calendar API.",
          "details": { "error_code": "AUTH_FAILURE" }
        }
        ```
*   **Callback/Webhook (For long-running tasks, if plugin uses this pattern):**
    *   Plugin initially returns HTTP 202 Accepted: `{"status": "processing", "task_id_mirrored": "task_def456"}`.
    *   Plugin later calls a pre-registered agent webhook: `POST /agent/callbacks/plugin/{task_id}` with a payload similar to the Success/Failure response.
```
