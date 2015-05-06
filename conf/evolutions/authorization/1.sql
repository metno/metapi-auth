# --- !Ups

CREATE TABLE authorized_keys (
    owner_id BIGSERIAL PRIMARY KEY,
    client_id UUID NOT NULL,
    client_secret BIGINT DEFAULT NULL,
    owner VARCHAR(128) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT unique_client_id UNIQUE(client_id)
);


# --- !Downs

DROP TABLE authorized_keys;
