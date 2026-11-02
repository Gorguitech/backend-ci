DROP INDEX uq_paiement_mensualite;

ALTER TABLE paiements_enfants ADD COLUMN balance_after NUMERIC(10,2);
ALTER TABLE paiements_enfants ADD COLUMN is_partial BOOLEAN;