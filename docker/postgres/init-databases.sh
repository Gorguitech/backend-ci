#!/usr/bin/env bash
#
# Execute une seule fois par l'image postgres, au premier demarrage (volume vide).
# Cree une base et un utilisateur dedie par service. Les mots de passe viennent de l'environnement.
# Chaque utilisateur est proprietaire de sa seule base ; l'acces des autres roles est retire.

set -euo pipefail

create_database() {
    local db="$1" user="$2" password="$3"

    if [[ -z "$password" ]]; then
        echo "Mot de passe manquant pour l'utilisateur $user" >&2
        exit 1
    fi

    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
         -v db="$db" -v usr="$user" -v pwd="$password" <<'SQL'
CREATE ROLE :"usr" LOGIN PASSWORD :'pwd';
CREATE DATABASE :"db" OWNER :"usr";
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
SQL
    echo "Base $db creee pour l'utilisateur $user"
}

create_database auth_db         auth_user         "${AUTH_DB_PASSWORD:-}"
create_database enfant_db       enfant_user       "${ENFANT_DB_PASSWORD:-}"
create_database personnel_db    personnel_user    "${PERSONNEL_DB_PASSWORD:-}"
create_database paiement_db     paiement_user     "${PAIEMENT_DB_PASSWORD:-}"
create_database comptabilite_db comptabilite_user "${COMPTABILITE_DB_PASSWORD:-}"
create_database audit_db        audit_user        "${AUDIT_DB_PASSWORD:-}"
