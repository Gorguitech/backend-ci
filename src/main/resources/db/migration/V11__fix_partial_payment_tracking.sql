
ALTER TABLE paiements_enfants ADD COLUMN expected_amount NUMERIC(10,2);
ALTER TABLE paiements_enfants ADD COLUMN excess_amount NUMERIC(10,2) NOT NULL DEFAULT 0;