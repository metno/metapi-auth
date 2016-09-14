# --- !Ups

-- All possible permissions
CREATE TABLE permissions (
  id INT NOT NULL,
  name VARCHAR(128) NOT NULL
);
ALTER TABLE permissions ADD CONSTRAINT unique_id UNIQUE(id);
ALTER TABLE permissions ADD CONSTRAINT unique_name UNIQUE(name);

-- mapping users to permissions
CREATE TABLE user_permissions (
  owner_id BIGINT NOT NULL REFERENCES authorized_keys(owner_id), -- there really should be a users table
  permission INT NOT NULL REFERENCES permissions(id)
);
ALTER TABLE user_permissions ADD CONSTRAINT unique_combinations UNIQUE(owner_id, permission);

INSERT INTO permissions(id, name) VALUES (0, 'auth.test.topsecret');
INSERT INTO permissions(id, name) VALUES (1, 'auth.test.topsecret.extended');

INSERT INTO authorized_keys (client_id, client_secret, email) VALUES ('cafebabe-feed-d00d-cafe-5eaf00d5a1ad', 'f007ba11-dad5-f1ed-dead-decafc0ffee5', 'root@localhost');

INSERT INTO user_permissions(owner_id, permission) SELECT owner_id, 0 FROM authorized_keys WHERE client_id = 'cafebabe-feed-d00d-badd-5eaf00d5a1ad';
INSERT INTO user_permissions(owner_id, permission) SELECT owner_id, 0 FROM authorized_keys WHERE client_id = 'cafebabe-feed-d00d-cafe-5eaf00d5a1ad';
INSERT INTO user_permissions(owner_id, permission) SELECT owner_id, 1 FROM authorized_keys WHERE client_id = 'cafebabe-feed-d00d-cafe-5eaf00d5a1ad';



# --- !Downs

DROP TABLE user_permissions;
DROP TABLE permissions;
DELETE FROM authorized_keys WHERE client_id = 'cafebabe-feed-d00d-good-5eaf00d5a1ad';
