CREATE TABLE enfants (
                         id                     BIGSERIAL PRIMARY KEY,
                         last_name              VARCHAR(100) NOT NULL,
                         first_name             VARCHAR(100) NOT NULL,
                         birth_date             DATE,
                         approximate_age        INTEGER,
                         gender                 VARCHAR(1) NOT NULL CHECK (gender IN ('M', 'F')),
                         guardian_name          VARCHAR(150) NOT NULL,
                         guardian_phone         VARCHAR(20) NOT NULL,
                         neighborhood           VARCHAR(150),
                         entry_date             DATE NOT NULL,
                         status                 VARCHAR(20) NOT NULL DEFAULT 'ACTIF'
                             CHECK (status IN ('ACTIF', 'SORTI', 'EN_VACANCES')),
                         room_group             VARCHAR(50),
                         registration_fee       NUMERIC(10,2) NOT NULL,
                         monthly_fee            NUMERIC(10,2) NOT NULL,
                         created_at             TIMESTAMP NOT NULL DEFAULT now(),

                         CONSTRAINT chk_birth_info CHECK (birth_date IS NOT NULL OR approximate_age IS NOT NULL)
);