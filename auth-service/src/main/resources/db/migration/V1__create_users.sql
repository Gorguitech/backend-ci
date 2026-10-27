-- Schema repris de la migration V1 du monolithe. Le compte admin initial n'est plus insere ici :
-- il est cree au premier demarrage a partir de ADMIN_INITIAL_PASSWORD_HASH (aucun hash dans le depot).
CREATE TABLE users (
    id                     BIGSERIAL PRIMARY KEY,
    last_name              VARCHAR(100) NOT NULL,
    first_name             VARCHAR(100) NOT NULL,
    username               VARCHAR(50) UNIQUE NOT NULL,
    password_hash          VARCHAR(255) NOT NULL,
    email                  VARCHAR(150),
    role                   VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'GESTIONNAIRE')),
    active                 BOOLEAN NOT NULL DEFAULT TRUE,
    must_change_password   BOOLEAN NOT NULL DEFAULT TRUE,
    recovery_code_hash     VARCHAR(255),
    created_at             TIMESTAMP NOT NULL DEFAULT now()
);
