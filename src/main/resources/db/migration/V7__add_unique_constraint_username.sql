-- Remove any duplicate usernames (keeps the oldest user with each username)
-- This prevents the unique constraint from failing on existing data
DELETE FROM users a USING users b
WHERE a.id > b.id AND a.username = b.username;

-- Add unique constraint to username column
ALTER TABLE users ADD CONSTRAINT uk_users_username UNIQUE (username);
