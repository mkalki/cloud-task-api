CREATE TABLE refresh_tokens(
    id BIGSERIAL PRIMARY KEY ,
    token_hash VARCHAR(255) NOT NULL UNIQUE ,
    expires_at TIMESTAMP NOT NULL ,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    user_id BIGINT NOT NULL
);