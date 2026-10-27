-- Schema consolide des migrations V5 et V6 du monolithe (table employes et matricule unique).
CREATE TABLE employes (
    id                BIGSERIAL PRIMARY KEY,
    matricule         VARCHAR(20) UNIQUE NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    first_name        VARCHAR(100) NOT NULL,
    phone             VARCHAR(20) NOT NULL,
    role_function     VARCHAR(30) NOT NULL
        CHECK (role_function IN ('MAITRE_CORANIQUE', 'MAITRE_FRANCAIS', 'SURVEILLANT',
                                 'CUISINIER', 'AGENT_ENTRETIEN', 'GARDIEN')),
    contract_type     VARCHAR(20) NOT NULL CHECK (contract_type IN ('TEMPS_PLEIN', 'PARTIEL')),
    base_salary       NUMERIC(10,2) NOT NULL,
    hire_date         DATE NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIF'
        CHECK (status IN ('ACTIF', 'EN_CONGE', 'ANCIEN')),
    created_at        TIMESTAMP NOT NULL DEFAULT now()
);

-- Recherches frequentes : liste par statut et controle de doublon a la creation.
CREATE INDEX idx_employes_status ON employes (status);
-- upper() : forme generee par Spring Data pour les recherches IgnoreCase
CREATE INDEX idx_employes_doublon ON employes (upper(first_name), upper(last_name), phone);
