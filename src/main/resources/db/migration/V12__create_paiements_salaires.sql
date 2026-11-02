CREATE TABLE paiements_salaires (
                                    id                      BIGSERIAL PRIMARY KEY,
                                    employe_id              BIGINT NOT NULL REFERENCES employes(id),
                                    registered_by_user_id   BIGINT NOT NULL REFERENCES users(id),
                                    registered_by_username  VARCHAR(50) NOT NULL,
                                    payment_method          VARCHAR(20) NOT NULL CHECK (payment_method IN ('ESPECES', 'WAVE', 'ORANGE_MONEY', 'VIREMENT')),
                                    amount                  NUMERIC(10,2) NOT NULL CHECK (amount > 0),
                                    expected_amount         NUMERIC(10,2) NOT NULL,
                                    concerned_month         DATE NOT NULL,
                                    payment_date            DATE NOT NULL DEFAULT CURRENT_DATE,
                                    status                  VARCHAR(10) NOT NULL DEFAULT 'VALIDE' CHECK (status IN ('VALIDE', 'ANNULE')),
                                    created_at              TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_paiement_salaire_employe ON paiements_salaires (employe_id);

CREATE UNIQUE INDEX uq_paiement_salaire_mois ON paiements_salaires (employe_id, concerned_month)
    WHERE status = 'VALIDE';