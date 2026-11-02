CREATE TABLE categories_depenses (
                                     id           BIGSERIAL PRIMARY KEY,
                                     name         VARCHAR(100) NOT NULL UNIQUE,
                                     description  VARCHAR(255),
                                     active       BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO categories_depenses (name) VALUES
                                           ('Riz'), ('Huile'), ('Sucre'), ('Gaz'), ('Eau/Électricité'),
                                           ('Santé'), ('Petit Matériel'), ('Transport');

CREATE TABLE depenses (
                          id                       BIGSERIAL PRIMARY KEY,
                          categorie_id             BIGINT NOT NULL REFERENCES categories_depenses(id),
                          amount                   NUMERIC(10,2) NOT NULL CHECK (amount > 0),
                          expense_date             DATE NOT NULL DEFAULT CURRENT_DATE,
                          description              VARCHAR(255),
                          registered_by_user_id    BIGINT NOT NULL REFERENCES users(id),
                          registered_by_username   VARCHAR(50) NOT NULL,
                          status                   VARCHAR(10) NOT NULL DEFAULT 'VALIDE' CHECK (status IN ('VALIDE', 'ANNULE')),
                          created_at               TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_depense_date ON depenses (expense_date);
CREATE INDEX idx_depense_categorie ON depenses (categorie_id);

CREATE TABLE autres_recettes (
                                 id                       BIGSERIAL PRIMARY KEY,
                                 source                   VARCHAR(150) NOT NULL,
                                 amount                   NUMERIC(10,2) NOT NULL CHECK (amount > 0),
                                 recette_date             DATE NOT NULL DEFAULT CURRENT_DATE,
                                 description              VARCHAR(255),
                                 registered_by_user_id    BIGINT NOT NULL REFERENCES users(id),
                                 registered_by_username   VARCHAR(50) NOT NULL,
                                 status                   VARCHAR(10) NOT NULL DEFAULT 'VALIDE' CHECK (status IN ('VALIDE', 'ANNULE')),
                                 created_at               TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_recette_date ON autres_recettes (recette_date);