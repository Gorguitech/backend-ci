ALTER TABLE paiements_enfants ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'VALIDE'
    CHECK (status IN ('VALIDE', 'ANNULE'));

DROP INDEX uq_paiement_inscription;
DROP INDEX uq_paiement_mensualite;

CREATE UNIQUE INDEX uq_paiement_inscription ON paiements_enfants (enfant_id)
    WHERE payment_type = 'INSCRIPTION' AND status = 'VALIDE';

CREATE UNIQUE INDEX uq_paiement_mensualite ON paiements_enfants (enfant_id, concerned_month)
    WHERE payment_type = 'MENSUALITE' AND status = 'VALIDE';