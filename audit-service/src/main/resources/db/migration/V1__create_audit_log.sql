-- Schema repris de la migration V4 du monolithe, plus event_id pour l'idempotence de la consommation RabbitMQ.
CREATE TABLE audit_log (
    id           BIGSERIAL PRIMARY KEY,
    event_id     UUID NOT NULL,
    user_id      BIGINT NOT NULL,
    username     VARCHAR(50) NOT NULL,
    action       VARCHAR(50) NOT NULL,
    entity_type  VARCHAR(50) NOT NULL,
    entity_id    BIGINT,
    details      VARCHAR(500),
    action_date  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_audit_event_id UNIQUE (event_id)
);

CREATE INDEX idx_audit_entity ON audit_log (entity_type, entity_id);
CREATE INDEX idx_audit_user ON audit_log (user_id);
