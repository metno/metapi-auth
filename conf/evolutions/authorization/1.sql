# --- !Ups

CREATE TABLE authorized_keys (
    owner_id BIGSERIAL PRIMARY KEY,
    --api_key UUID PRIMARY KEY, -- to be used when/if migrating to postgresql
    api_key VARCHAR(64),
    owner VARCHAR(128) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT unique_api_key UNIQUE(api_key)
);


# --- !Downs

DROP TABLE authorized_keys;
