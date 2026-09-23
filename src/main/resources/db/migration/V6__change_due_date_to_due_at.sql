ALTER TABLE tasks
ALTER COLUMN due_date TYPE TIMESTAMP
    USING due_date::timestamp;

ALTER TABLE tasks
    RENAME COLUMN due_date TO due_at;