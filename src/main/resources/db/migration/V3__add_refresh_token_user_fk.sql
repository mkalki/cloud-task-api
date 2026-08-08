ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id)
            REFERENCES users(id);