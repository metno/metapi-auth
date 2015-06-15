# --- !Ups

CREATE TABLE authorized_keys (
    owner_id BIGSERIAL,
    client_id UUID NOT NULL,
    client_secret UUID NOT NULL,
    email VARCHAR(128) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
ALTER TABLE authorized_keys ADD CONSTRAINT unique_owner_id UNIQUE(owner_id);
ALTER TABLE authorized_keys ADD CONSTRAINT unique_client_id UNIQUE(client_id);

INSERT INTO authorized_keys (client_id, client_secret, email) VALUES ('cafebabe-feed-d00d-badd-5eaf00d5a1ad', 'f007ba11-dad5-f1ed-dead-decafc0ffee5', 'root@localhost')

# --- !Downs

DROP TABLE authorized_keys;
