-- Create Users table
CREATE TABLE users (
    user_id UUID PRIMARY KEY,
    platform_id VARCHAR(255),
    source_platform VARCHAR(255),
    username VARCHAR(255),
    display_name VARCHAR(255),
    email VARCHAR(255) UNIQUE,
    preferences JSONB,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_users_platform_id ON users(platform_id);
CREATE INDEX idx_users_email ON users(email);

-- Create Conversations table
CREATE TABLE conversations (
    conversation_id UUID PRIMARY KEY,
    platform_conversation_id VARCHAR(255),
    source_platform VARCHAR(255),
    type VARCHAR(50),
    last_activity_timestamp TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    summary TEXT,
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_conversations_platform_id ON conversations(platform_conversation_id);
CREATE INDEX idx_conversations_last_activity ON conversations(last_activity_timestamp);

-- Create Messages table
CREATE TABLE messages (
    message_id UUID PRIMARY KEY,
    conversation_id UUID REFERENCES conversations(conversation_id),
    user_id UUID REFERENCES users(user_id),
    platform_message_id VARCHAR(255),
    content TEXT NOT NULL,
    embedding_id VARCHAR(255),
    role VARCHAR(50) NOT NULL, -- USER, AGENT
    metadata JSONB,
    "timestamp" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_messages_conversation_id ON messages(conversation_id);
CREATE INDEX idx_messages_user_id ON messages(user_id);
CREATE INDEX idx_messages_embedding_id ON messages(embedding_id);
CREATE INDEX idx_messages_timestamp ON messages("timestamp");


-- Create Tasks table
CREATE TABLE tasks (
    task_id UUID PRIMARY KEY,
    description TEXT NOT NULL,
    created_by_user_id UUID REFERENCES users(user_id) NOT NULL,
    assigned_to_user_id UUID REFERENCES users(user_id),
    status VARCHAR(50) NOT NULL, -- PENDING, IN_PROGRESS, COMPLETED, BLOCKED, CANCELED
    due_date TIMESTAMPTZ,
    priority VARCHAR(50), -- LOW, MEDIUM, HIGH
    parent_task_id UUID REFERENCES tasks(task_id),
    conversation_id UUID REFERENCES conversations(conversation_id),
    platform_context_link VARCHAR(2048),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_due_date ON tasks(due_date);
CREATE INDEX idx_tasks_created_by ON tasks(created_by_user_id);
CREATE INDEX idx_tasks_assigned_to ON tasks(assigned_to_user_id);

-- Create TaskHistory table
CREATE TABLE task_history (
    history_id UUID PRIMARY KEY,
    task_id UUID REFERENCES tasks(task_id) NOT NULL,
    user_id UUID REFERENCES users(user_id), -- User who made the change
    event_type VARCHAR(255) NOT NULL,
    change_details JSONB,
    "timestamp" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_task_history_task_id ON task_history(task_id);

-- Create Plugins table
CREATE TABLE plugins (
    plugin_id UUID PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    version VARCHAR(255),
    description TEXT,
    configuration_schema JSONB,
    input_schema JSONB NOT NULL,
    output_schema JSONB NOT NULL,
    entry_point VARCHAR(255) NOT NULL,
    entry_point_type VARCHAR(50) NOT NULL, -- java_class, spring_bean
    is_enabled BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_plugins_name ON plugins(name);

-- Create Actions table
CREATE TABLE actions (
    action_id UUID PRIMARY KEY,
    task_id UUID REFERENCES tasks(task_id),
    plugin_id UUID REFERENCES plugins(plugin_id) NOT NULL,
    parameters JSONB,
    status VARCHAR(50) NOT NULL, -- PENDING, RUNNING, SUCCESS, FAILED
    start_time TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMPTZ,
    execution_log TEXT,
    output JSONB
);
CREATE INDEX idx_actions_task_id ON actions(task_id);
CREATE INDEX idx_actions_plugin_id ON actions(plugin_id);
CREATE INDEX idx_actions_status ON actions(status);

-- Trigger function to update 'updated_at' columns automatically (PostgreSQL specific)
CREATE OR REPLACE FUNCTION trigger_set_timestamp()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to tables with 'updated_at'
CREATE TRIGGER set_timestamp_users
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE PROCEDURE trigger_set_timestamp();

CREATE TRIGGER set_timestamp_tasks
BEFORE UPDATE ON tasks
FOR EACH ROW
EXECUTE PROCEDURE trigger_set_timestamp();
