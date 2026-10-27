-- Etat final des migrations V8 a V11 du monolithe (paiements_enfants), sans cle etrangere vers d'autres bases :
-- l'eleve et l'utilisateur sont references par leur identifiant, et l'identite de l'eleve est copiee
-- au moment du paiement (enfant_matricule, enfant_full_name, guardian_name) pour les recus et l'historique.
CREATE TABLE paiements_enfants (
    id                       BIGSERIAL PRIMARY KEY,
    enfant_id                BIGINT NOT NULL,
    enfant_matricule         VARCHAR(20) NOT NULL,
    enfant_full_name         VARCHAR(201) NOT NULL,
    guardian_name            VARCHAR(150) NOT NULL,
    registered_by_user_id    BIGINT NOT NULL,
    registered_by_username   VARCHAR(50) NOT NULL,
    payment_type             VARCHAR(20) NOT NULL CHECK (payment_type IN ('INSCRIPTION', 'MENSUALITE')),
    payment_method           VARCHAR(20) NOT NULL CHECK (payment_method IN ('ESPECES', 'WAVE', 'ORANGE_MONEY', 'VIREMENT')),
    amount                   NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    concerned_month          DATE,
    payment_date             DATE NOT NULL DEFAULT CURRENT_DATE,
    status                   VARCHAR(10) NOT NULL DEFAULT 'VALIDE' CHECK (status IN ('VALIDE', 'ANNULE')),
    balance_after            NUMERIC(10,2),
    is_partial               BOOLEAN,
    expected_amount          NUMERIC(10,2),
    excess_amount            NUMERIC(10,2) NOT NULL DEFAULT 0,
    created_at               TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_paiement_enfant ON paiements_enfants (enfant_id);
CREATE INDEX idx_paiement_enfant_mois ON paiements_enfants (concerned_month, payment_type, status);
CREATE INDEX idx_paiement_enfant_date ON paiements_enfants (payment_date, status);

-- Une seule inscription valide par eleve (V9 du monolithe). Les mensualites peuvent etre partielles (V10) :
-- le depassement du montant du est empeche par un verrou applicatif par eleve et par mois.
CREATE UNIQUE INDEX uq_paiement_inscription ON paiements_enfants (enfant_id)
    WHERE payment_type = 'INSCRIPTION' AND status = 'VALIDE';
