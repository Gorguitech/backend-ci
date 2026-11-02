CREATE TABLE employes (
                          id                BIGSERIAL PRIMARY KEY,
                          last_name         VARCHAR(100) NOT NULL,
                          first_name        VARCHAR(100) NOT NULL,
                          phone             VARCHAR(20) NOT NULL,
                          role_function     VARCHAR(30) NOT NULL
                              CHECK (role_function IN (
                                                       'MAITRE_CORANIQUE', 'MAITRE_FRANCAIS', 'SURVEILLANT',
                                                       'CUISINIER', 'AGENT_ENTRETIEN', 'GARDIEN'
                                  )),
                          contract_type     VARCHAR(20) NOT NULL CHECK (contract_type IN ('TEMPS_PLEIN', 'PARTIEL')),
                          base_salary       NUMERIC(10,2) NOT NULL,
                          hire_date         DATE NOT NULL,
                          status            VARCHAR(20) NOT NULL DEFAULT 'ACTIF'
                              CHECK (status IN ('ACTIF', 'EN_CONGE', 'ANCIEN')),
                          created_at        TIMESTAMP NOT NULL DEFAULT now()
);