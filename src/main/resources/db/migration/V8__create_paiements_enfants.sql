-- src/main/resources/db/migration/V8__create_paiements_enfants.sql

CREATE TABLE paiements_enfants (
                                   id                       BIGSERIAL PRIMARY KEY,
                                   enfant_id                BIGINT NOT NULL REFERENCES enfants(id),
                                   registered_by_user_id    BIGINT NOT NULL REFERENCES users(id),
                                   registered_by_username   VARCHAR(50) NOT NULL,
                                   payment_type             VARCHAR(20) NOT NULL CHECK (payment_type IN ('INSCRIPTION', 'MENSUALITE')),
                                   payment_method           VARCHAR(20) NOT NULL CHECK (payment_method IN ('ESPECES', 'WAVE', 'ORANGE_MONEY', 'VIREMENT')),
                                   amount                   NUMERIC(10,2) NOT NULL CHECK (amount > 0),
                                   concerned_month          DATE,
                                   payment_date             DATE NOT NULL DEFAULT CURRENT_DATE,
                                   created_at               TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_paiement_enfant ON paiements_enfants (enfant_id);

-- Un seul paiement d'inscription possible par enfant, jamais deux
CREATE UNIQUE INDEX uq_paiement_inscription ON paiements_enfants (enfant_id)
    WHERE payment_type = 'INSCRIPTION';

-- Une seule mensualité par enfant et par mois concerné
CREATE UNIQUE INDEX uq_paiement_mensualite ON paiements_enfants (enfant_id, concerned_month)
    WHERE payment_type = 'MENSUALITE';