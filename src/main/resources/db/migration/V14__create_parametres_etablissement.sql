CREATE TABLE parametres_etablissement (
                                          id             BIGSERIAL PRIMARY KEY,
                                          name           VARCHAR(150) NOT NULL,
                                          address        VARCHAR(255),
                                          phone          VARCHAR(20),
                                          email          VARCHAR(150),
                                          director_name  VARCHAR(150),
                                          logo_path      VARCHAR(255),
                                          updated_at     TIMESTAMP NOT NULL DEFAULT now()
);

INSERT INTO parametres_etablissement (name, updated_at)
VALUES ('Daara (à configurer)', now());