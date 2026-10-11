-- Add indexes on deleted column for better query performance
-- These indexes improve performance when filtering by deleted = false
-- which is the most common query pattern in the application

CREATE INDEX idx_tasks_deleted ON tasks(deleted) WHERE deleted = false;
CREATE INDEX idx_users_deleted ON users(deleted) WHERE deleted = false;
