-- src/main/resources/db/migration/V4__create_audit_log.sql

CREATE TABLE audit_log (
                           id           BIGSERIAL PRIMARY KEY,
                           user_id      BIGINT NOT NULL,
                           username     VARCHAR(50) NOT NULL,
                           action       VARCHAR(50) NOT NULL,
                           entity_type  VARCHAR(50) NOT NULL,
                           entity_id    BIGINT,
                           details      VARCHAR(500),
                           action_date  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_entity ON audit_log (entity_type, entity_id);